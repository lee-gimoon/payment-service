package com.example.payment.chat.api;

import com.example.payment.chat.application.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 고객의 상담. 방은 토큰의 회원 ID로 찾으므로 다른 고객의 대화를 지정할 수 없다. */
@RestController
@Tag(name = "Chat")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/chat/messages")
    @Operation(summary = "내 상담 대화 조회", description = "최근 50개. before에 메시지 번호를 주면 그 이전 대화를, "
            + "after에 주면 그 이후 대화를 불러온다. after는 WebSocket을 다시 연결한 뒤 놓친 메시지를 채울 때 쓴다.")
    public ChatHistoryResponse messages(@AuthenticationPrincipal Jwt customer,
                                        @RequestParam(required = false) Long before,
                                        @RequestParam(required = false) Long after) {
        return chat.customerHistory(customer.getSubject(), new ChatService.Page(before, after));
    }

    @PostMapping("/chat/messages")
    @Operation(summary = "상담 메시지 보내기",
            description = "첫 메시지면 상담방을 만든다. 같은 clientMessageId로 다시 보내면 저장된 메시지를 200으로 돌려준다.")
    public ResponseEntity<ChatMessageResponse> send(@AuthenticationPrincipal Jwt customer,
                                                    @Valid @RequestBody ChatMessageRequest request) {
        ChatService.Sent sent = chat.sendAsCustomer(customerOf(customer), request);
        return ResponseEntity.status(sent.created() ? 201 : 200).body(sent.message());
    }

    @PostMapping("/chat/read")
    @Operation(summary = "관리자 답변 읽음 표시")
    public ResponseEntity<Void> read(@AuthenticationPrincipal Jwt customer,
                                     @Valid @RequestBody ChatReadRequest request) {
        chat.markReadByCustomer(customer.getSubject(), request.lastReadMessageId());
        return ResponseEntity.noContent().build();
    }

    // 화면 헤더와 같은 규칙으로 한글 이름은 성과 이름을 붙여 쓰고, 이름이 없으면 이메일을 쓴다.
    private static ChatService.Customer customerOf(Jwt token) {
        String email = token.getClaimAsString("email");
        String given = Objects.requireNonNullElse(token.getClaimAsString("given_name"), "");
        String family = Objects.requireNonNullElse(token.getClaimAsString("family_name"), "");
        String name = (family + given).matches(".*[가-힣].*") ? family + given : (given + " " + family).strip();
        if (name.isEmpty()) {
            name = email != null ? email : "고객";
        }
        return new ChatService.Customer(token.getSubject(), name, email);
    }
}
