package com.inetbank.investment;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import feign.FeignException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InvestmentJwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final InvestmentJwtService jwtService;
    private final IdentityCustomerClient identityClient;
        private static final Logger LOG =
        LoggerFactory.getLogger(InvestmentJwtAuthenticationFilter.class);
    public InvestmentJwtAuthenticationFilter(
            InvestmentJwtService jwtService,
            IdentityCustomerClient identityClient) {

        this.jwtService = jwtService;
        this.identityClient = identityClient;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null
                && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext()
                        .getAuthentication() == null) {

            try {
                Claims claims = jwtService.parse(header.substring(7));

                String email = claims.getSubject();
                String tokenRole = claims.get("role", String.class);

                IdentityCustomerClient.CustomerProfileResponse user =
                        identityClient.findByEmail(email);

                if ("ACTIVE".equals(user.status())
                        && tokenRole != null
                        && tokenRole.equals(user.role())) {

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user.email(),
                                    null,
                                    AuthorityUtils.createAuthorityList(
                                            "ROLE_" + user.role()
                                    )
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }

            } catch (FeignException exception) {
                LOG.warn(
                        "Investment authentication: Identity lookup failed. HTTP status={}",
                        exception.status()
                );
                SecurityContextHolder.clearContext();

                } catch (JwtException | IllegalArgumentException exception) {
                LOG.warn(
                        "Investment authentication: JWT validation failed. Exception type={}",
                        exception.getClass().getSimpleName()
                );
                SecurityContextHolder.clearContext();
                }
        }
        
        filterChain.doFilter(request, response);
    }
}