use hbb_common::{
    anyhow::{anyhow, bail},
    log, ResultType,
};
use ndk::media::{
    media_codec::{MediaCodec, MediaCodecDirection, MediaFormat},
    NdkMediaError,
};
use std::{
    sync::atomic::{AtomicBool, Ordering},
    time::Duration,
};

use crate::{
    android::ffi::{get_codec_info, MediaCodecInfo},
    codec::enable_hwcodec_option,
    convert::*,
    CodecFormat, ImageFormat, ImageRgb,
};

const H264_MIME_TYPE: &str = "video/avc";
const H265_MIME_TYPE: &str = "video/hevc";
const VP9_MIME_TYPE: &str = "video/x-vnd.on2.vp9";
const AV1_MIME_TYPE: &str = "video/av01";

const COLOR_FORMAT_YUV420_PLANAR: i32 = 19;
const COLOR_FORMAT_YUV420_SEMIPLANAR: i32 = 21;

pub static H264_DECODER_SUPPORT: AtomicBool = AtomicBool::new(false);
pub static H265_DECODER_SUPPORT: AtomicBool = AtomicBool::new(false);
pub static VP9_DECODER_SUPPORT: AtomicBool = AtomicBool::new(false);
pub static AV1_DECODER_SUPPORT: AtomicBool = AtomicBool::new(false);

crate::generate_call_macro!(call_yuv, false);

pub struct MediaCodecDecoder {
    decoder: MediaCodec,
    codec_name: String,
    backend_name: String,
    requested_color_format: i32,
}

impl MediaCodecDecoder {
    pub fn new(format: CodecFormat) -> Option<Self> {
        if !enable_hwcodec_option() {
            return None;
        }
        let mime = mime_type(format)?;
        let infos = get_codec_info()?;
        let codec = select_hardware_decoder(&infos.codecs, mime)?;

        // Byte-buffer output is required by the current Flutter renderer. Do not
        // select a surface-only or flexible-only decoder: without Image plane
        // metadata its chroma layout cannot be interpreted safely.
        let requested_color_format = if codec.i420 {
            COLOR_FORMAT_YUV420_PLANAR
        } else if codec.nv12 {
            COLOR_FORMAT_YUV420_SEMIPLANAR
        } else {
            log::warn!(
                "MediaCodec hardware decoder {} only exposes flexible/surface output for {mime}",
                codec.name
            );
            return None;
        };

        let width = infos
            .w
            .clamp(codec.min_width.max(1), codec.max_width.max(1));
        let height = infos
            .h
            .clamp(codec.min_height.max(1), codec.max_height.max(1));
        create_media_codec(codec, mime, width, height, requested_color_format)
    }

    pub fn hardware_available(format: CodecFormat) -> bool {
        let Some(mime) = mime_type(format) else {
            return false;
        };
        get_codec_info().is_some_and(|infos| {
            select_hardware_decoder(&infos.codecs, mime)
                .is_some_and(|codec| codec.i420 || codec.nv12)
        })
    }

    pub fn backend_name(&self) -> &str {
        &self.backend_name
    }

    pub fn decode(&mut self, data: &[u8], pts: i64, rgb: &mut ImageRgb) -> ResultType<bool> {
        let Some(mut input_buffer) = self
            .decoder
            .dequeue_input_buffer(Duration::from_millis(10))?
        else {
            return Ok(false);
        };
        let input = input_buffer.buffer_mut();
        if data.len() > input.len() {
            bail!(
                "MediaCodec input frame {} exceeds buffer {} for {}",
                data.len(),
                input.len(),
                self.codec_name
            );
        }
        input[..data.len()].copy_from_slice(data);
        self.decoder
            .queue_input_buffer(input_buffer, 0, data.len(), pts.max(0) as u64, 0)?;

        // INFO_OUTPUT_FORMAT_CHANGED / INFO_OUTPUT_BUFFERS_CHANGED are returned
        // by ndk 0.7 as UnknownResult. They are state notifications, not decode
        // failures; the next dequeue returns the real frame.
        for _ in 0..3 {
            let output = match self
                .decoder
                .dequeue_output_buffer(Duration::from_millis(35))
            {
                Ok(Some(output)) => output,
                Ok(None) => return Ok(false),
                Err(NdkMediaError::UnknownResult(status)) => {
                    log::debug!(
                        "MediaCodec output state change {:?} for {}: {}",
                        status,
                        self.codec_name,
                        self.decoder.output_format()
                    );
                    continue;
                }
                Err(error) => return Err(anyhow!(error)),
            };

            let result = self.copy_output_to_rgb(&output.buffer(), rgb);
            self.decoder.release_output_buffer(output, false)?;
            return result.map(|_| true);
        }
        Ok(false)
    }

    fn copy_output_to_rgb(&self, buf: &[u8], rgb: &mut ImageRgb) -> ResultType<()> {
        let format = self.decoder.output_format();
        let coded_width = positive(format.i32("width"), "width")? as usize;
        let coded_height = positive(format.i32("height"), "height")? as usize;
        let stride = format.i32("stride").unwrap_or(coded_width as i32).max(1) as usize;
        let slice_height = format
            .i32("slice-height")
            .unwrap_or(coded_height as i32)
            .max(coded_height as i32) as usize;
        let crop_left = format.i32("crop-left").unwrap_or(0).max(0) as usize;
        let crop_top = format.i32("crop-top").unwrap_or(0).max(0) as usize;
        let crop_right = format
            .i32("crop-right")
            .unwrap_or(coded_width as i32 - 1)
            .max(crop_left as i32) as usize;
        let crop_bottom = format
            .i32("crop-bottom")
            .unwrap_or(coded_height as i32 - 1)
            .max(crop_top as i32) as usize;
        let width = crop_right - crop_left + 1;
        let height = crop_bottom - crop_top + 1;
        let color_format = format
            .i32("color-format")
            .unwrap_or(self.requested_color_format);

        rgb.w = width;
        rgb.h = height;
        let dst_stride = aligned_stride(width * 4, rgb.align());
        rgb.raw.resize(dst_stride * height, 0);

        let y_base = crop_top
            .checked_mul(stride)
            .and_then(|value| value.checked_add(crop_left))
            .ok_or_else(|| anyhow!("MediaCodec Y offset overflow"))?;
        let uv_base = stride
            .checked_mul(slice_height)
            .ok_or_else(|| anyhow!("MediaCodec UV offset overflow"))?;

        match color_format {
            COLOR_FORMAT_YUV420_PLANAR => {
                let uv_stride = (stride + 1) / 2;
                let uv_height = (slice_height + 1) / 2;
                let u_base = uv_base + (crop_top / 2) * uv_stride + crop_left / 2;
                let v_base =
                    uv_base + uv_stride * uv_height + (crop_top / 2) * uv_stride + crop_left / 2;
                ensure_plane(buf, y_base, stride, height, width, "Y")?;
                ensure_plane(
                    buf,
                    u_base,
                    uv_stride,
                    (height + 1) / 2,
                    (width + 1) / 2,
                    "U",
                )?;
                ensure_plane(
                    buf,
                    v_base,
                    uv_stride,
                    (height + 1) / 2,
                    (width + 1) / 2,
                    "V",
                )?;
                let convert = match rgb.fmt() {
                    ImageFormat::ARGB => I420ToARGB,
                    ImageFormat::ABGR => I420ToABGR,
                    _ => bail!("Unsupported MediaCodec RGB target: {:?}", rgb.fmt()),
                };
                call_yuv!(convert(
                    unsafe { buf.as_ptr().add(y_base) },
                    stride as i32,
                    unsafe { buf.as_ptr().add(u_base) },
                    uv_stride as i32,
                    unsafe { buf.as_ptr().add(v_base) },
                    uv_stride as i32,
                    rgb.raw.as_mut_ptr(),
                    dst_stride as i32,
                    width as i32,
                    height as i32,
                ));
            }
            COLOR_FORMAT_YUV420_SEMIPLANAR => {
                let uv_offset = uv_base + (crop_top / 2) * stride + (crop_left / 2) * 2;
                ensure_plane(buf, y_base, stride, height, width, "Y")?;
                ensure_plane(buf, uv_offset, stride, (height + 1) / 2, width, "UV")?;
                let convert = match rgb.fmt() {
                    ImageFormat::ARGB => NV12ToARGB,
                    ImageFormat::ABGR => NV12ToABGR,
                    _ => bail!("Unsupported MediaCodec RGB target: {:?}", rgb.fmt()),
                };
                call_yuv!(convert(
                    unsafe { buf.as_ptr().add(y_base) },
                    stride as i32,
                    unsafe { buf.as_ptr().add(uv_offset) },
                    stride as i32,
                    rgb.raw.as_mut_ptr(),
                    dst_stride as i32,
                    width as i32,
                    height as i32,
                ));
            }
            other => bail!(
                "Unsupported MediaCodec color format {other} from {}",
                self.codec_name
            ),
        }
        Ok(())
    }
}

fn mime_type(format: CodecFormat) -> Option<&'static str> {
    match format {
        CodecFormat::H264 => Some(H264_MIME_TYPE),
        CodecFormat::H265 => Some(H265_MIME_TYPE),
        CodecFormat::VP9 => Some(VP9_MIME_TYPE),
        CodecFormat::AV1 => Some(AV1_MIME_TYPE),
        _ => None,
    }
}

fn select_hardware_decoder<'a>(
    codecs: &'a [MediaCodecInfo],
    mime: &str,
) -> Option<&'a MediaCodecInfo> {
    codecs
        .iter()
        .filter(|codec| {
            !codec.is_encoder
                && codec.hw == Some(true)
                && codec.mime_type.eq_ignore_ascii_case(mime)
                && (codec.i420 || codec.nv12)
        })
        .max_by_key(|codec| {
            let concrete_layout = usize::from(codec.i420 || codec.nv12);
            (
                concrete_layout,
                codec.max_width.saturating_mul(codec.max_height),
            )
        })
}

fn create_media_codec(
    codec_info: &MediaCodecInfo,
    mime: &str,
    width: usize,
    height: usize,
    color_format: i32,
) -> Option<MediaCodecDecoder> {
    let codec = MediaCodec::from_codec_name(&codec_info.name)?;
    let media_format = MediaFormat::new();
    media_format.set_str("mime", mime);
    media_format.set_i32("width", width as i32);
    media_format.set_i32("height", height as i32);
    media_format.set_i32("max-width", codec_info.max_width as i32);
    media_format.set_i32("max-height", codec_info.max_height as i32);
    media_format.set_i32("color-format", color_format);
    if codec_info.low_latency == Some(true) {
        media_format.set_i32("low-latency", 1);
    }
    if let Err(error) = codec.configure(&media_format, None, MediaCodecDirection::Decoder) {
        log::error!(
            "Failed to configure MediaCodec decoder {} ({mime}): {error:?}",
            codec_info.name
        );
        return None;
    }
    if let Err(error) = codec.start() {
        log::error!(
            "Failed to start MediaCodec decoder {} ({mime}): {error:?}",
            codec_info.name
        );
        return None;
    }
    let backend_name = format!("Android MediaCodec hardware ({})", codec_info.name);
    log::info!(
        "Created {backend_name}, mime={mime}, configured={}x{}, color={color_format}",
        width,
        height
    );
    Some(MediaCodecDecoder {
        decoder: codec,
        codec_name: codec_info.name.clone(),
        backend_name,
        requested_color_format: color_format,
    })
}

fn positive(value: Option<i32>, name: &str) -> ResultType<i32> {
    match value {
        Some(value) if value > 0 => Ok(value),
        _ => bail!("MediaCodec output {name} is missing or invalid"),
    }
}

fn aligned_stride(value: usize, align: usize) -> usize {
    let align = align.max(1);
    (value + align - 1) & !(align - 1)
}

fn ensure_plane(
    buf: &[u8],
    offset: usize,
    stride: usize,
    rows: usize,
    row_bytes: usize,
    name: &str,
) -> ResultType<()> {
    let required = if rows == 0 {
        offset
    } else {
        offset
            .checked_add((rows - 1).saturating_mul(stride))
            .and_then(|value| value.checked_add(row_bytes))
            .ok_or_else(|| anyhow!("MediaCodec {name} plane size overflow"))?
    };
    if required > buf.len() {
        bail!(
            "MediaCodec {name} plane requires {required} bytes, output has {}",
            buf.len()
        );
    }
    Ok(())
}

pub fn check_mediacodec() {
    std::thread::spawn(|| {
        // MainActivity publishes the Java MediaCodecList asynchronously during
        // Flutter engine setup. Wait briefly so the capability flags describe
        // real hardware rather than the old MIME-only assumption.
        for _ in 0..20 {
            if get_codec_info().is_some() {
                break;
            }
            std::thread::sleep(Duration::from_millis(100));
        }
        H264_DECODER_SUPPORT.store(
            MediaCodecDecoder::hardware_available(CodecFormat::H264),
            Ordering::SeqCst,
        );
        H265_DECODER_SUPPORT.store(
            MediaCodecDecoder::hardware_available(CodecFormat::H265),
            Ordering::SeqCst,
        );
        VP9_DECODER_SUPPORT.store(
            MediaCodecDecoder::hardware_available(CodecFormat::VP9),
            Ordering::SeqCst,
        );
        AV1_DECODER_SUPPORT.store(
            MediaCodecDecoder::hardware_available(CodecFormat::AV1),
            Ordering::SeqCst,
        );
        log::info!(
            "MediaCodec hardware decode: h264={}, h265={}, vp9={}, av1={}",
            H264_DECODER_SUPPORT.load(Ordering::SeqCst),
            H265_DECODER_SUPPORT.load(Ordering::SeqCst),
            VP9_DECODER_SUPPORT.load(Ordering::SeqCst),
            AV1_DECODER_SUPPORT.load(Ordering::SeqCst),
        );
    });
}
