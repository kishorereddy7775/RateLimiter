package com.entities;

import java.time.LocalDateTime;

public record Request(int id,String client,String token, LocalDateTime timeStamp) {

}
