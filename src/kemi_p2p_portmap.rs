//! macOS host-side IPv4 direct-access mapping for KEMI.
//!
//! RustDesk already has a direct TCP listener (default: 21118), but it is
//! normally opt-in and has no router mapping lifecycle. This module keeps the
//! exact same TCP port mapped through PCP, NAT-PMP or UPnP IGD. The PAD can
//! then try `<host public IP>:21118` when ordinary UDP hole punching reports
//! an asymmetric NAT. All traffic still enters RustDesk's normal encrypted,
//! authenticated direct-server path.

use std::{
    net::{IpAddr, Ipv4Addr, SocketAddr, SocketAddrV4, UdpSocket},
    sync::atomic::{AtomicBool, Ordering},
    thread,
    time::Duration,
};

use hbb_common::{log, rand};
use igd_next::{search_gateway, PortMappingProtocol};

static STARTED: AtomicBool = AtomicBool::new(false);
static READY: AtomicBool = AtomicBool::new(false);

/// Whether the last mapping renewal succeeded. This is intentionally only a
/// readiness hint: direct TCP still performs the normal RustDesk handshake.
pub fn is_ready() -> bool {
    READY.load(Ordering::Acquire)
}

/// Start one long-lived TCP mapping lifecycle for the direct listener.
pub fn start(port: u16) {
    if port == 0 {
        log::error!("KEMI P2P port mapping skipped: invalid direct port");
        return;
    }
    if STARTED.swap(true, Ordering::SeqCst) {
        return;
    }
    if let Err(err) = thread::Builder::new()
        .name("kemi-p2p-portmap".to_owned())
        .spawn(move || renew_mapping(port))
    {
        log::warn!("KEMI P2P port mapping thread failed: {err}");
    }
}

/// A random external port cannot be used with the existing RustDesk peer
/// signal, which contains only the peer's public address. Therefore success
/// requires the fixed public port to be exactly the direct listener port.
fn renew_mapping(port: u16) {
    loop {
        let result = mapping_context().and_then(|(local, gateway)| match gateway {
            // PCP/NAT-PMP need a unicast default gateway.  UPnP discovery does
            // not, so missing route metadata must not prevent an IGD attempt.
            Some(gateway) => map_pcp(local, gateway, port)
                .or_else(|_| map_nat_pmp(local, gateway, port))
                .or_else(|_| map_upnp(local, port)),
            None => map_upnp(local, port),
        });
        match result {
            Ok((protocol, address, lease_secs)) => {
                READY.store(true, Ordering::Release);
                log::warn!(
                    "KEMI P2P mapped direct TCP port ready: {address} ({protocol}); lease {lease_secs}s"
                );
                // Renew temporary mappings before expiry. Infinite UPnP leases
                // are checked hourly so a router reboot is repaired.
                thread::sleep(Duration::from_secs((lease_secs / 2).clamp(60, 3600)));
            }
            Err(reason) => {
                READY.store(false, Ordering::Release);
                log::warn!(
                    "KEMI P2P automatic fixed-port mapping unavailable ({reason}); direct IPv4 cannot be guaranteed and relay remains available"
                );
                thread::sleep(Duration::from_secs(300));
            }
        }
    }
}

fn mapping_context() -> Result<(Ipv4Addr, Option<Ipv4Addr>), String> {
    let interface = default_net::get_default_interface()
        .map_err(|err| format!("cannot determine default interface: {err}"))?;
    let local = interface
        .ipv4
        .first()
        .map(|item| item.addr)
        .ok_or_else(|| "default interface has no IPv4 address".to_owned())?;
    let gateway = interface.gateway.and_then(|item| match item.ip_addr {
        IpAddr::V4(ip) => Some(ip),
        IpAddr::V6(_) => None,
    });
    Ok((local, gateway))
}

fn map_pcp(
    local: Ipv4Addr,
    gateway: Ipv4Addr,
    port: u16,
) -> Result<(&'static str, SocketAddrV4, u64), String> {
    let socket = mapping_socket(local, gateway)?;
    let nonce = rand::random::<[u8; 12]>();
    let mut request = [0_u8; 60];
    request[0] = 2; // PCP version
    request[1] = 1; // MAP opcode
    request[4..8].copy_from_slice(&3600_u32.to_be_bytes());
    request[18..20].copy_from_slice(&[0xff, 0xff]); // IPv4-mapped client address
    request[20..24].copy_from_slice(&local.octets());
    request[24..36].copy_from_slice(&nonce);
    request[36] = 6; // TCP
    request[40..42].copy_from_slice(&port.to_be_bytes());
    request[42..44].copy_from_slice(&port.to_be_bytes());
    socket
        .send(&request)
        .map_err(|err| format!("PCP send failed: {err}"))?;

    let mut response = [0_u8; 128];
    let received = socket
        .recv(&mut response)
        .map_err(|err| format!("PCP did not respond: {err}"))?;
    if received < 60 || response[0] != 2 || response[1] != 0x81 || response[3] != 0 {
        return Err("PCP rejected fixed TCP mapping".to_owned());
    }
    if response[24..36] != nonce || response[36] != 6 {
        return Err("PCP mapping response did not match request".to_owned());
    }
    let external_port = u16::from_be_bytes([response[42], response[43]]);
    let external_ip = Ipv4Addr::new(response[56], response[57], response[58], response[59]);
    let lifetime = u32::from_be_bytes(response[4..8].try_into().unwrap()) as u64;
    fixed_public_mapping("PCP", external_ip, external_port, port, lifetime)
}

fn map_nat_pmp(
    local: Ipv4Addr,
    gateway: Ipv4Addr,
    port: u16,
) -> Result<(&'static str, SocketAddrV4, u64), String> {
    let socket = mapping_socket(local, gateway)?;
    let mut request = [0_u8; 12];
    request[1] = 2; // NAT-PMP TCP mapping
    request[4..6].copy_from_slice(&port.to_be_bytes());
    request[6..8].copy_from_slice(&port.to_be_bytes());
    request[8..12].copy_from_slice(&3600_u32.to_be_bytes());
    socket
        .send(&request)
        .map_err(|err| format!("NAT-PMP send failed: {err}"))?;
    let mut mapping_response = [0_u8; 32];
    let received = socket
        .recv(&mut mapping_response)
        .map_err(|err| format!("NAT-PMP did not respond: {err}"))?;
    if received < 16
        || mapping_response[1] != 130
        || u16::from_be_bytes([mapping_response[2], mapping_response[3]]) != 0
        || u16::from_be_bytes([mapping_response[8], mapping_response[9]]) != port
    {
        return Err("NAT-PMP rejected fixed TCP mapping".to_owned());
    }
    let external_port = u16::from_be_bytes([mapping_response[10], mapping_response[11]]);
    let lifetime = u32::from_be_bytes(mapping_response[12..16].try_into().unwrap()) as u64;

    socket
        .send(&[0, 0])
        .map_err(|err| format!("NAT-PMP address query failed: {err}"))?;
    let mut address_response = [0_u8; 32];
    let received = socket
        .recv(&mut address_response)
        .map_err(|err| format!("NAT-PMP address query did not respond: {err}"))?;
    if received < 12
        || address_response[1] != 128
        || u16::from_be_bytes([address_response[2], address_response[3]]) != 0
    {
        return Err("NAT-PMP external address query failed".to_owned());
    }
    let external_ip = Ipv4Addr::new(
        address_response[8],
        address_response[9],
        address_response[10],
        address_response[11],
    );
    fixed_public_mapping("NAT-PMP", external_ip, external_port, port, lifetime)
}

fn map_upnp(local: Ipv4Addr, port: u16) -> Result<(&'static str, SocketAddrV4, u64), String> {
    let gateway = search_gateway(Default::default())
        .map_err(|err| format!("UPnP discovery failed: {err}"))?;
    gateway
        .add_port(
            PortMappingProtocol::TCP,
            port,
            SocketAddr::from((local, port)),
            3600,
            "KEMI RustDesk IPv4 P2P",
        )
        .map_err(|err| format!("UPnP fixed TCP mapping rejected: {err}"))?;
    let external_ip = match gateway.get_external_ip() {
        Ok(IpAddr::V4(ip)) => ip,
        Ok(IpAddr::V6(_)) => return Err("UPnP reported IPv6, expected IPv4".to_owned()),
        Err(err) => return Err(format!("UPnP external address query failed: {err}")),
    };
    fixed_public_mapping("UPnP", external_ip, port, port, 3600)
}

fn mapping_socket(local: Ipv4Addr, gateway: Ipv4Addr) -> Result<UdpSocket, String> {
    let socket = UdpSocket::bind(SocketAddrV4::new(local, 0))
        .map_err(|err| format!("cannot open mapping socket: {err}"))?;
    socket
        .connect(SocketAddrV4::new(gateway, 5351))
        .map_err(|err| format!("cannot reach default gateway: {err}"))?;
    socket
        .set_read_timeout(Some(Duration::from_millis(1500)))
        .map_err(|err| format!("cannot set mapping timeout: {err}"))?;
    Ok(socket)
}

fn fixed_public_mapping(
    protocol: &'static str,
    ip: Ipv4Addr,
    external_port: u16,
    requested_port: u16,
    lease_secs: u64,
) -> Result<(&'static str, SocketAddrV4, u64), String> {
    let address = SocketAddrV4::new(ip, external_port);
    if external_port != requested_port {
        return Err(format!(
            "{protocol} returned external port {external_port}, expected fixed port {requested_port}"
        ));
    }
    if !is_public(address) {
        return Err(format!("{protocol} returned non-public address {address}"));
    }
    Ok((protocol, address, lease_secs))
}

fn is_public(address: SocketAddrV4) -> bool {
    let ip = address.ip();
    !ip.is_private()
        && !ip.is_loopback()
        && !ip.is_link_local()
        && !ip.is_unspecified()
        && !ip.is_broadcast()
        // RFC 6598 shared carrier-grade NAT range is not externally reachable.
        && !(ip.octets()[0] == 100 && (64..=127).contains(&ip.octets()[1]))
}
