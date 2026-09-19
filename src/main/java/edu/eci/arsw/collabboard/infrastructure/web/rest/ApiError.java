package edu.eci.arsw.collabboard.infrastructure.web.rest;
import java.time.Instant;
public record ApiError(Instant timestamp,int status,String code,String message,String path) {}
