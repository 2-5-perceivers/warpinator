use std::collections::VecDeque;

pub mod compression;
pub mod transfer_receiver;
pub mod transfer_sender;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(i32)]
enum FileType {
    File = 1,
    Directory = 2,
    Symlink = 3,
}

impl TryFrom<i32> for FileType {
    type Error = ();

    fn try_from(value: i32) -> Result<Self, Self::Error> {
        match value {
            1 => Ok(Self::File),
            2 => Ok(Self::Directory),
            3 => Ok(Self::Symlink),
            _ => Err(()),
        }
    }
}

impl From<FileType> for i32 {
    fn from(value: FileType) -> Self {
        value as i32
    }
}

pub(crate) struct MovingAverageCalculator {
    samples: VecDeque<u64>,
    window: usize,
}

impl MovingAverageCalculator {
    pub fn new(window: usize) -> Self {
        Self { samples: VecDeque::with_capacity(window), window }
    }

    /// Push a new chunk size, returns (avg_bytes_per_sec)
    pub fn push(&mut self, bytes: u64, elapsed_secs: f64) -> u64 {
        if elapsed_secs <= 0.0 {
            return self.current_average();
        }

        let bps = (bytes as f64 / elapsed_secs) as u64;
        self.samples.push_back(bps);

        if self.samples.len() > self.window {
            self.samples.pop_front();
        }

        self.current_average()
    }

    fn current_average(&self) -> u64 {
        if self.samples.is_empty() {
            return 0;
        }
        self.samples.iter().sum::<u64>() / self.samples.len() as u64
    }
}
