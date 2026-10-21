package com.skala.supplyquote.supplyquote_backend.service;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import com.skala.supplyquote.supplyquote_backend.auth.CurrentUser;
import com.skala.supplyquote.supplyquote_backend.domain.UserType;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import com.skala.supplyquote.supplyquote_backend.domain.RfqStatus;
import com.skala.supplyquote.supplyquote_backend.internal.dto.InternalRfqDto;
import com.skala.supplyquote.supplyquote_backend.messaging.AnalysisDispatch;
import com.skala.supplyquote.supplyquote_backend.web.dto.RfqDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RfqCommandService {
    private final JdbcTemplate jdbc;
    private final AnalysisDispatch dispatch;
    private final RfqQueryService queries;
    private final Path filesDir;
    private final CurrentUser current;

    public RfqCommandService(JdbcTemplate jdbc, AnalysisDispatch dispatch, RfqQueryService queries,
                             @Value("${app.rfq-files-dir:./rfq-files}") String filesDir,
                             CurrentUser current) {
        this.jdbc = jdbc;
        this.dispatch = dispatch;
        this.queries = queries;
        this.filesDir = Path.of(filesDir).toAbsolutePath().normalize();
        this.current = current;
    }

    @Transactional
    public RfqDto.UploadAccepted upload(MultipartFile file, String number) {
        var user = current.get();
        if (user.userType() != UserType.CUSTOMER)
            throw new ApiException(ErrorCode.FORBIDDEN, "고객만 RFQ를 접수할 수 있습니다.");
        if (file.isEmpty() || number == null || !number.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,29}")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "마크다운 파일과 RFQ 번호(영문·숫자·-·_, 30자 이내)가 필요합니다.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".md")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ".md 파일만 업로드할 수 있습니다.");
        }
        Path target = filesDir.resolve("uploads").resolve(number + ".md");
        Path temporary;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "rfq-", ".tmp");
            file.transferTo(temporary);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "RFQ 파일을 저장하지 못했습니다.");
        }
        try {
            Long rfqId;
            try {
                rfqId = jdbc.queryForObject("""
                        insert into rfqs(rfq_number,customer_company_id,uploaded_by,file_path,status)
                        values (?,?,?,?,'RECEIVED') returning rfq_id
                        """, Long.class, number, user.companyId(), user.userId(), "uploads/" + number + ".md");
            } catch (DuplicateKeyException e) {
                throw new ApiException(ErrorCode.CONFLICT_STATE, "이미 접수된 RFQ 번호입니다: " + number);
            }
            UUID taskId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            String threadId = "task-" + taskId;
            jdbc.update("""
                    insert into ai_tasks(task_id,rfq_number,requested_by,status,thread_id,request_id)
                    values (?,?,?,'QUEUED',?,?)
                    """, taskId, number, user.userId(), threadId, requestId);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            dispatch.requested(taskId, rfqId, user.userId(), number, threadId, requestId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) try { Files.deleteIfExists(target); } catch (IOException ignored) { }
                }
            });
            return new RfqDto.UploadAccepted(rfqId, number, taskId, TaskStatus.QUEUED);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "RFQ 파일을 저장하지 못했습니다.");
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    @Transactional
    public InternalRfqDto.RfqInfo patch(String number, InternalRfqDto.RfqPatch patch) {
        var current = queries.internal(number);
        if (patch.status() != null && patch.status() != current.status()) {
            if (current.status() != RfqStatus.RECEIVED || patch.status() != RfqStatus.IN_REVIEW)
                throw new ApiException(ErrorCode.CONFLICT_STATE, "RFQ 상태는 RECEIVED에서 IN_REVIEW로만 변경할 수 있습니다.");
        }
        jdbc.update("""
                update rfqs set status=coalesce(?,status), eau=coalesce(?,eau),
                sample_qty=coalesce(?,sample_qty), sample_due_date=coalesce(?,sample_due_date),
                sop_date=coalesce(?,sop_date), target_price_krw=coalesce(?,target_price_krw),
                quote_due_date=coalesce(?,quote_due_date) where rfq_number=?
                """, patch.status() == null ? null : patch.status().name(), patch.eau(), patch.sampleQty(),
                patch.sampleDueDate(), patch.sopDate(), patch.targetPriceKrw(), patch.quoteDueDate(), number);
        return queries.internal(number);
    }
}
