package com.ssafy.auth.dto.response;

import com.ssafy.auth.dto.token.JwtTokens;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginResponse {
    private JwtTokens token; // JWT 토큰

    public LoginResponse(JwtTokens token) {
        this.token = token;
    }
}