package roomescape.auth.core;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieAuthorizationManager implements AuthorizationManager {

    private final String TOKEN_COOKIE_NAME = "token";

    @Override
    public void setAuthorization(HttpServletResponse httpServletResponse, String token) {
        ResponseCookie responseCookie = ResponseCookie.from(TOKEN_COOKIE_NAME, token)
                .build();
        httpServletResponse.setHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }

    @Override
    public String getAuthorization(HttpServletRequest httpServletRequest) {
        Cookie[] cookies = httpServletRequest.getCookies();
        checkCookieExist(cookies);

        Cookie cookie = extractTokenCookie(cookies)
                .orElseThrow(() -> new SecurityException("토큰에 대한 쿠키가 없어서 회원 정보를 찾을 수 없습니다. 다시 로그인해주세요."));
        return cookie.getValue();
    }

    private void checkCookieExist(Cookie[] cookies) {
        if (cookies == null) {
            throw new SecurityException("쿠키가 없어서 회원 정보를 찾을 수 없습니다. 다시 로그인해주세요.");
        }
    }

    private Optional<Cookie> extractTokenCookie(Cookie[] cookies) {
        return Arrays.stream(cookies)
                .filter(cookie -> cookie.getName().equals(TOKEN_COOKIE_NAME))
                .findFirst()
                .or(Optional::empty);
    }
}
