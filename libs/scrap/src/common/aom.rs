// Stub aom module for debug macOS build - libaom not available from source

use crate::codec::EncoderApi;
use crate::{EncodeInput, EncodeYuvFormat, Pixfmt, GoogleImage};
use hbb_common::{anyhow::anyhow, message_proto::{Chroma, VideoFrame}, ResultType};

#[derive(Clone, Copy, Debug)]
pub struct AomEncoderConfig {
    pub width: u32,
    pub height: u32,
    pub quality: f32,
    pub keyframe_interval: Option<usize>,
}

pub struct AomEncoder;
pub struct AomDecoder;
pub struct Image;

impl GoogleImage for Image {
    fn width(&self) -> usize { 0 }
    fn height(&self) -> usize { 0 }
    fn stride(&self) -> Vec<i32> { vec![] }
    fn planes(&self) -> Vec<*mut u8> { vec![] }
    fn chroma(&self) -> Chroma { Chroma::I420 }
}

impl Image {
    pub fn new() -> Self { Image }
    pub fn is_null(&self) -> bool { true }
    pub fn format(&self) -> i32 { 0 }
    pub fn inner(&self) -> *const u8 { std::ptr::null() }
}

impl AomEncoder {
    pub fn encode<'a>(&'a mut self, _ms: i64, _data: &[u8], _stride_align: usize) -> ResultType<Vec<()>> {
        Err(anyhow!("AOM encoder not available").into())
    }
}

impl EncoderApi for AomEncoder {
    fn new(_cfg: crate::codec::EncoderCfg, _i444: bool) -> ResultType<Self> {
        Err(anyhow!("AOM encoder not available in debug build").into())
    }
    fn encode_to_message(&mut self, _frame: EncodeInput, _ms: i64) -> ResultType<VideoFrame> {
        Err(anyhow!("AOM encoder not available").into())
    }
    fn yuvfmt(&self) -> EncodeYuvFormat {
        EncodeYuvFormat { pixfmt: Pixfmt::I420, w: 0, h: 0, stride: vec![], u: 0, v: 0 }
    }
    fn set_quality(&mut self, _ratio: f32) -> ResultType<()> { Ok(()) }
    fn bitrate(&self) -> u32 { 0 }
    fn support_changing_quality(&self) -> bool { false }
    fn latency_free(&self) -> bool { false }
    fn is_hardware(&self) -> bool { false }
    fn disable(&self) {}
}

impl AomDecoder {
    pub fn new() -> ResultType<Self> {
        Err(anyhow!("AOM decoder not available in debug build").into())
    }
    pub fn decode<'a>(&'a mut self, _data: &[u8]) -> ResultType<Vec<Image>> {
        Err(anyhow!("AOM decoder not available").into())
    }
    pub fn flush<'a>(&'a mut self) -> ResultType<Vec<Image>> {
        Err(anyhow!("AOM decoder not available").into())
    }
}

pub(crate) mod webrtc {
    use hbb_common::{anyhow::anyhow, ResultType};
    pub fn enc_cfg(_i: *const u8, _config: super::AomEncoderConfig, _i444: bool) -> ResultType<()> {
        Err(anyhow!("AOM not available").into())
    }
    pub fn set_controls(_ctx: *mut u8, _cfg: &()) -> ResultType<()> {
        Err(anyhow!("AOM not available").into())
    }
}
