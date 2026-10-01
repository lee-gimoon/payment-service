package com.example.payment.chat.api;

import com.example.payment.chat.application.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 쇼핑몰 관리자의 상담 응대. /admin/** 경로라 shop-admin 역할이 있어야 호출할 수 있다. */
@RestController
@Tag(name = "Admin Chat")
public class AdminChatController {
    private final ChatService chat;

    public AdminChatController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/admin/chat/rooms")
    @Operation(summary = "상담방 목록", description = "최근 메시지 순으로 최대 100개. waiting=true면 답변 대기 중인 방만 준다.")
    public List<ChatRoomResponse> rooms(@RequestParam(defaultValue = "false") boolean waiting) {
        return chat.roomsForAdmin(waiting);
    }

    @GetMapping("/admin/chat/rooms/{roomId}/messages")
    @Operation(summary = "상담 대화 조회", description = "최근 50개. before에 메시지 번호를 주면 그 이전 대화를, "
            + "after에 주면 그 이후 대화를 불러온다.")
    public ChatHistoryResponse messages(@PathVariable String roomId, @RequestParam(required = false) Long before,
                                        @RequestParam(required = false) Long after) {
        return chat.adminHistory(roomId, new ChatService.Page(before, after));
    }

    @PostMapping("/admin/chat/rooms/{roomId}/messages")
    @Operation(summary = "답장 보내기", description = "같은 clientMessageId로 다시 보내면 저장된 메시지를 200으로 돌려준다.")
    public ResponseEntity<ChatMessageResponse> reply(@AuthenticationPrincipal Jwt admin, @PathVariable String roomId,
                                                     @Valid @RequestBody ChatMessageRequest request) {
        ChatService.Sent sent = chat.sendAsAdmin(roomId, admin.getSubject(), request);
        return ResponseEntity.status(sent.created() ? 201 : 200).body(sent.message());
    }

    @PostMapping("/admin/chat/rooms/{roomId}/read")
    @Operation(summary = "고객 메시지 읽음 표시")
    public ResponseEntity<Void> read(@PathVariable String roomId, @Valid @RequestBody ChatReadRequest request) {
        chat.markReadByAdmin(roomId, request.lastReadMessageId());
        return ResponseEntity.noContent().build();
    }
}
