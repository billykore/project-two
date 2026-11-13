package org.kore.myapp.api.controller;

import org.junit.jupiter.api.Test;
import org.kore.myapp.api.handler.JsonAuthenticationEntryPoint;
import org.kore.myapp.api.handler.ProblemDetailWriter;
import org.kore.myapp.api.request.ConfirmPasswordResetRequest;
import org.kore.myapp.config.SecurityConfig;
import org.kore.myapp.core.exception.InvalidPasswordResetException;
import org.kore.myapp.core.exception.InvalidResetCodeException;
import org.kore.myapp.core.exception.PasswordResetBusyException;
import org.kore.myapp.core.port.PasswordResetUseCase;
import org.kore.myapp.core.valueobject.Contact;
import org.kore.myapp.core.valueobject.ContactChannel;
import org.kore.myapp.core.valueobject.FieldError;
import org.kore.myapp.core.valueobject.RequestPasswordResetCommand;
import org.kore.myapp.core.valueobject.ResetPasswordCommand;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordResetController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, ProblemDetailWriter.class})
class PasswordResetControllerTest {

    private static final String CONFIRM_BODY =
            "{\"email\": \"budi@example.com\", \"code\": \"004213\", \"newPassword\": \"newsecret99\"}";

    private static final String GENERIC_MESSAGE = "If an account exists for this contact, a code has been sent to it.";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PasswordResetUseCase useCase;

    @Autowired
    private JwtEncoder jwtEncoder;

    // --- request ---------------------------------------------------------------------------------------------

    @Test
    void requestByEmailIsAcceptedWithTheGenericMessageAndNeedsNoToken() throws Exception {
        mvc.perform(request("{\"email\": \"Budi@Example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

        ArgumentCaptor<RequestPasswordResetCommand> command =
                ArgumentCaptor.forClass(RequestPasswordResetCommand.class);
        verify(useCase).requestReset(command.capture());
        assertThat(command.getValue().contact()).isEqualTo(new Contact(ContactChannel.EMAIL, "budi@example.com"));
    }

    @Test
    void requestByPhoneIsAcceptedWithTheSameMessage() throws Exception {
        mvc.perform(request("{\"phone\": \"+6281234567890\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

        ArgumentCaptor<RequestPasswordResetCommand> command =
                ArgumentCaptor.forClass(RequestPasswordResetCommand.class);
        verify(useCase).requestReset(command.capture());
        assertThat(command.getValue().contact()).isEqualTo(new Contact(ContactChannel.PHONE, "+6281234567890"));
    }

    @Test
    void requestWithAnInvalidContactListsTheField() throws Exception {
        mvc.perform(request("{\"phone\": \"081234567890\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid password reset"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must be in international format, e.g. +6281234567890"));
        verify(useCase, never()).requestReset(any());
    }

    @Test
    void requestWithBothContactsListsBothFields() throws Exception {
        mvc.perform(request("{\"email\": \"budi@example.com\", \"phone\": \"+6281234567890\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[1].field").value("phone"))
                .andExpect(jsonPath("$.errors[0].message").value("provide either email or phone"));
    }

    @Test
    void requestWithNeitherContactListsBothFields() throws Exception {
        mvc.perform(request("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void requestWithAnUnreadableBodyIsMalformed() throws Exception {
        mvc.perform(request("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
        mvc.perform(post("/auth/password-reset/request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    // --- confirm ---------------------------------------------------------------------------------------------

    @Test
    void confirmChangesThePasswordAndRespondsWithoutABody() throws Exception {
        mvc.perform(confirm("{\"email\": \"budi@example.com\", \"code\": \"004213\", "
                        + "\"newPassword\": \"newsecret99\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        ArgumentCaptor<ResetPasswordCommand> command = ArgumentCaptor.forClass(ResetPasswordCommand.class);
        verify(useCase).resetPassword(command.capture());
        assertThat(command.getValue().contact()).isEqualTo(new Contact(ContactChannel.EMAIL, "budi@example.com"));
        assertThat(command.getValue().code()).isEqualTo("004213");
        assertThat(command.getValue().newPassword()).isEqualTo("newsecret99");
    }

    @Test
    void confirmWithABadCodeFormatAndAShortPasswordListsBothFields() throws Exception {
        mvc.perform(confirm("{\"email\": \"budi@example.com\", \"code\": \"12\", \"newPassword\": \"short12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid password reset"))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].message").value("must be 6 digits"))
                .andExpect(jsonPath("$.errors[1].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[1].message").value("must be at least 8 characters"));
        verify(useCase, never()).resetPassword(any());
    }

    @Test
    void confirmWithARefusedPasswordReportsTheReason() throws Exception {
        doThrow(new InvalidPasswordResetException(List.of(
                new FieldError("newPassword", "must not be one of your last 3 passwords"))))
                .when(useCase).resetPassword(any());

        mvc.perform(confirm("{\"email\": \"budi@example.com\", \"code\": \"004213\", "
                        + "\"newPassword\": \"newsecret99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid password reset"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be one of your last 3 passwords"));
    }

    @Test
    void confirmWithAnInvalidCodeGivesTheSingleGenericError() throws Exception {
        doThrow(new InvalidResetCodeException()).when(useCase).resetPassword(any());

        mvc.perform(confirm("{\"email\": \"budi@example.com\", \"code\": \"004213\", "
                        + "\"newPassword\": \"newsecret99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid or expired code"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired code"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void confirmWhileTheAccountIsBusyAsksTheClientToRetry() throws Exception {
        doThrow(new PasswordResetBusyException()).when(useCase).resetPassword(any());

        mvc.perform(confirm("{\"email\": \"budi@example.com\", \"code\": \"004213\", "
                        + "\"newPassword\": \"newsecret99\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Password reset busy"));
    }

    @Test
    void confirmWithAnUnreadableBodyIsMalformed() throws Exception {
        mvc.perform(confirm("[1, 2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void theConfirmRequestDescriptionShowsNeitherTheCodeNorThePassword() {
        String text = new ConfirmPasswordResetRequest("budi@example.com", null, "004213", "newsecret99").toString();

        assertThat(text).doesNotContain("004213").doesNotContain("newsecret99");
    }

    // --- a token sent anyway is ignored ---------------------------------------------------------------------------

    @Test
    void aValidTokenIsIgnored() throws Exception {
        String token = token(Instant.now().plusSeconds(600));

        mvc.perform(request("{\"email\": \"budi@example.com\"}").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted());
        mvc.perform(confirm(CONFIRM_BODY).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void anExpiredTokenIsIgnored() throws Exception {
        String token = token(Instant.now().minusSeconds(600));

        mvc.perform(request("{\"email\": \"budi@example.com\"}").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted());
        mvc.perform(confirm(CONFIRM_BODY).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void anInvalidTokenIsIgnored() throws Exception {
        mvc.perform(request("{\"phone\": \"+6281234567890\"}").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isAccepted());
        mvc.perform(confirm(CONFIRM_BODY).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isNoContent());
    }

    private String token(Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("myapp")
                .subject("42")
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static MockHttpServletRequestBuilder request(String json) {
        return post("/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockHttpServletRequestBuilder confirm(String json) {
        return post("/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
