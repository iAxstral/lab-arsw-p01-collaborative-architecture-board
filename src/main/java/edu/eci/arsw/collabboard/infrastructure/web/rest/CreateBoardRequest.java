package edu.eci.arsw.collabboard.infrastructure.web.rest;
import jakarta.validation.constraints.NotBlank;
public record CreateBoardRequest(@NotBlank(message="name is required") String name) {}
