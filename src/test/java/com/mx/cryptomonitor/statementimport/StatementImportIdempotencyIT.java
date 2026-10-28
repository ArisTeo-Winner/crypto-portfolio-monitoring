package com.mx.cryptomonitor.statementimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

class StatementImportIdempotencyIT extends UserModuleIntegrationTest {

  @Test
  void reuploadingTheSameGbmStatementDoesNotDuplicateTransactions() throws Exception {
    Tokens tokens = registerAndLogin();
    MockMultipartFile statement =
        new MockMultipartFile(
            "file",
            "gbm-statement-sample.pdf",
            "application/pdf",
            readResource("statementimport/gbm-statement-sample.pdf"));

    String firstJobId = uploadAndGetJobId("/api/v1/me/broker/gbm/statements", statement, tokens);
    awaitCompletion(firstJobId, tokens);

    int transactionsAfterFirstUpload = countUserTransactions(tokens);
    assertThat(transactionsAfterFirstUpload).isGreaterThan(0);

    String secondJobId = uploadAndGetJobId("/api/v1/me/broker/gbm/statements", statement, tokens);
    awaitCompletion(secondJobId, tokens);

    int transactionsAfterSecondUpload = countUserTransactions(tokens);
    assertThat(transactionsAfterSecondUpload).isEqualTo(transactionsAfterFirstUpload);
  }

  @Test
  void reuploadingTheSameDriveWealthConfirmationDoesNotDuplicateTransactions() throws Exception {
    Tokens tokens = registerAndLogin();
    MockMultipartFile confirmation =
        new MockMultipartFile(
            "files",
            "drivewealth-confirmation-sample.pdf",
            "application/pdf",
            readResource("statementimport/drivewealth-confirmation-sample.pdf"));

    String firstJobId =
        uploadBatchAndGetFirstJobId(
            "/api/v1/me/broker/gbm/drivewealth-confirmations", confirmation, tokens);
    awaitCompletion(firstJobId, tokens);

    int transactionsAfterFirstUpload = countUserTransactions(tokens);
    assertThat(transactionsAfterFirstUpload).isEqualTo(1);

    String secondJobId =
        uploadBatchAndGetFirstJobId(
            "/api/v1/me/broker/gbm/drivewealth-confirmations", confirmation, tokens);
    awaitCompletion(secondJobId, tokens);

    int transactionsAfterSecondUpload = countUserTransactions(tokens);
    assertThat(transactionsAfterSecondUpload).isEqualTo(transactionsAfterFirstUpload);
  }

  @Test
  void deletingADriveWealthTransactionAndReuploadingRecoversIt() throws Exception {
    Tokens tokens = registerAndLogin();
    MockMultipartFile confirmation =
        new MockMultipartFile(
            "files",
            "drivewealth-confirmation-sample.pdf",
            "application/pdf",
            readResource("statementimport/drivewealth-confirmation-sample.pdf"));

    String firstJobId =
        uploadBatchAndGetFirstJobId(
            "/api/v1/me/broker/gbm/drivewealth-confirmations", confirmation, tokens);
    awaitCompletion(firstJobId, tokens);
    List<String> ids = listUserTransactionIds(tokens);
    assertThat(ids).hasSize(1);

    // El usuario borra la transacción desde la app...
    deleteTransaction(ids.get(0), tokens);
    assertThat(countUserTransactions(tokens)).isZero();

    // ...y re-sube el mismo archivo para recuperarla.
    String secondJobId =
        uploadBatchAndGetFirstJobId(
            "/api/v1/me/broker/gbm/drivewealth-confirmations", confirmation, tokens);
    awaitCompletion(secondJobId, tokens);

    // Se recupera (sin error de idempotencia) y no se duplica.
    assertThat(countUserTransactions(tokens)).isEqualTo(1);
  }

  @Test
  void deletingOneTransactionFromAGbmStatementAndReuploadingRecoversOnlyThatOne() throws Exception {
    Tokens tokens = registerAndLogin();
    MockMultipartFile statement =
        new MockMultipartFile(
            "file",
            "gbm-statement-sample.pdf",
            "application/pdf",
            readResource("statementimport/gbm-statement-sample.pdf"));

    String firstJobId = uploadAndGetJobId("/api/v1/me/broker/gbm/statements", statement, tokens);
    awaitCompletion(firstJobId, tokens);
    List<String> ids = listUserTransactionIds(tokens);
    int total = ids.size();
    assertThat(total).isGreaterThan(1);

    // Borra UNA de varias.
    deleteTransaction(ids.get(0), tokens);
    assertThat(countUserTransactions(tokens)).isEqualTo(total - 1);

    // Re-sube el mismo archivo: recupera solo la borrada; el resto se omite como duplicado.
    String secondJobId = uploadAndGetJobId("/api/v1/me/broker/gbm/statements", statement, tokens);
    awaitCompletion(secondJobId, tokens);

    assertThat(countUserTransactions(tokens)).isEqualTo(total);
  }

  private String uploadAndGetJobId(String url, MockMultipartFile file, Tokens tokens)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart(url).file(file).header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isAccepted())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.jobId");
  }

  private String uploadBatchAndGetFirstJobId(String url, MockMultipartFile file, Tokens tokens)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart(url).file(file).header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isAccepted())
            .andReturn();
    List<String> jobIds = JsonPath.read(result.getResponse().getContentAsString(), "$[*].jobId");
    return jobIds.get(0);
  }

  private void awaitCompletion(String jobId, Tokens tokens) throws Exception {
    String status = "QUEUED";
    for (int attempt = 0;
        attempt < 40 && !"COMPLETED".equals(status) && !"DEAD_LETTER".equals(status);
        attempt++) {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/me/broker/gbm/import-jobs/{jobId}", jobId)
                      .header("Authorization", "Bearer " + tokens.accessToken()))
              .andExpect(status().isOk())
              .andReturn();
      status = JsonPath.read(result.getResponse().getContentAsString(), "$.status");
      if (!"COMPLETED".equals(status) && !"DEAD_LETTER".equals(status)) {
        Thread.sleep(500);
      }
    }
    assertThat(status).isEqualTo("COMPLETED");
  }

  private int countUserTransactions(Tokens tokens) throws Exception {
    return listUserTransactionIds(tokens).size();
  }

  private List<String> listUserTransactionIds(Tokens tokens) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/me/transactions")
                    .header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$[*].transactionId");
  }

  private void deleteTransaction(String transactionId, Tokens tokens) throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/me/transactions/{transactionId}", transactionId)
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());
  }

  private byte[] readResource(String resource) throws IOException {
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      return in.readAllBytes();
    }
  }
}
