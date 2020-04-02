package uk.thepragmaticdev.security;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.thepragmaticdev.exception.ApiError;
import uk.thepragmaticdev.exception.ApiException;
import uk.thepragmaticdev.exception.code.AccountCode;

public class JwtTokenFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(JwtTokenFilter.class);

  private JwtTokenProvider jwtTokenProvider;

  public JwtTokenFilter(JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Override
  protected void doFilterInternal(//
      HttpServletRequest httpServletRequest, //
      HttpServletResponse httpServletResponse, //
      FilterChain filterChain) throws ServletException, IOException {
    String token = jwtTokenProvider.resolveToken(httpServletRequest);
    try {
      if (token != null && jwtTokenProvider.validateToken(token)) {
        Authentication auth = jwtTokenProvider.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    } catch (ApiException ex) {
      SecurityContextHolder.clearContext();

      ApiError responseBody = new ApiError(//
          AccountCode.INVALID_EXPIRED_TOKEN.getStatus(), //
          AccountCode.INVALID_EXPIRED_TOKEN.getMessage() //
      );
      logger.warn("{}", responseBody);
      httpServletResponse.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      httpServletResponse.setStatus(AccountCode.INVALID_EXPIRED_TOKEN.getStatus().value());
      httpServletResponse.getWriter().write(responseBody.toString());
      return;
    }
    filterChain.doFilter(httpServletRequest, httpServletResponse);
  }
}