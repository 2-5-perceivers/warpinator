use std::io::{Read, Write};

use flate2::Compression;
use flate2::read::ZlibDecoder;
use flate2::write::ZlibEncoder;

/// Compresses a file chunk
pub fn compress_chunk(data: &[u8]) -> Result<Vec<u8>, std::io::Error> {
    if data.is_empty() {
        return Ok(Vec::new());
    }
    let mut encoder = ZlibEncoder::new(Vec::new(), Compression::new(2));
    encoder.write_all(data)?;
    encoder.finish()
}

/// Decompresses a zlib-compressed chunk
pub fn decompress_chunk(data: &[u8]) -> Result<Vec<u8>, std::io::Error> {
    if data.is_empty() {
        return Ok(Vec::new());
    }
    let mut decoder = ZlibDecoder::new(data);
    let mut decompressed = Vec::new();
    decoder.read_to_end(&mut decompressed)?;
    Ok(decompressed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_compress_and_decompress() {
        let original =
            b"Hello, World! This is a test string to verify Zlib compression in Warpinator RS.";
        let compressed = compress_chunk(original).expect("compression failed");
        let decompressed = decompress_chunk(&compressed).expect("decompression failed");
        assert_eq!(original.to_vec(), decompressed);
    }

    #[test]
    fn test_compression_reduces_size_for_repetitive_data() {
        let original = b"A".repeat(10000);
        let compressed = compress_chunk(&original).expect("compression failed");
        assert!(compressed.len() < original.len());
        let decompressed = decompress_chunk(&compressed).expect("decompression failed");
        assert_eq!(original, decompressed);
    }

    #[test]
    fn test_empty_chunk() {
        let compressed = compress_chunk(b"").expect("compression failed");
        assert!(compressed.is_empty());
        let decompressed = decompress_chunk(&compressed).expect("decompression failed");
        assert!(decompressed.is_empty());
    }
}
