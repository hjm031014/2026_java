package com.nsu.team.chat;

import com.nsu.team.common.ApiResponse;
import com.nsu.team.communication.dto.PagedItems;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1/chat/rooms")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) { this.service = service; }

    @PostMapping
    ResponseEntity<ApiResponse<ChatDtos.RoomResponse>> createRoom(
            @Valid @RequestBody ChatDtos.CreateRoomRequest request) {
        var result = service.createRoom(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .location(URI.create("/api/v1/chat/rooms/" + result.value().id()))
                .body(ApiResponse.of(result.value()));
    }

    @GetMapping
    ApiResponse<PagedItems<ChatDtos.RoomResponse>> listRooms(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.of(service.listRooms(cursor, limit));
    }

    @GetMapping("/{roomId}/messages")
    ApiResponse<ChatDtos.MessagePage> listMessages(
            @PathVariable Long roomId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Long afterSequence,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.of(service.listMessages(roomId, cursor, afterSequence, limit));
    }

    @PostMapping("/{roomId}/messages")
    ResponseEntity<ApiResponse<ChatDtos.MessageResponse>> sendMessage(
            @PathVariable Long roomId, @Valid @RequestBody ChatDtos.SendMessageRequest request) {
        var result = service.sendMessage(roomId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.of(result.value()));
    }
}
