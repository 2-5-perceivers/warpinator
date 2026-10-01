use warpinator_lib::types::message::{Direction, Message};

#[uniffi::remote(Enum)]
pub enum Direction {
    Sent,
    Received,
}

#[uniffi::remote(Record)]
pub struct Message {
    pub uuid: String,
    pub remote_uuid: String,
    pub direction: Direction,
    pub timestamp: u64,
    pub content: String,
}
