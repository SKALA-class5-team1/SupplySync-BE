package com.skala.supplyquote.supplyquote_backend.service;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import com.skala.supplyquote.supplyquote_backend.auth.UserAccountService;
import com.skala.supplyquote.supplyquote_backend.domain.RfqStatus;
import com.skala.supplyquote.supplyquote_backend.internal.dto.InternalRfqDto;
import com.skala.supplyquote.supplyquote_backend.web.dto.RfqDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RfqQueryService {
    private final JdbcTemplate jdbc;
    private final Path filesDir;

    public RfqQueryService(JdbcTemplate jdbc, @Value("${app.rfq-files-dir:./rfq-files}") String filesDir) {
        this.jdbc = jdbc;
        this.filesDir = Path.of(filesDir).toAbsolutePath().normalize();
    }

    public List<RfqDto.Summary> list(UserAccountService.Account user, boolean mine) {
        return jdbc.query("""
                select r.rfq_id, r.rfq_number, c.name as company_name, r.item, r.status, r.received_at
                from rfqs r join companies c on c.company_id = r.customer_company_id
                where (?='MANAGER' or r.customer_company_id=?) and (?=false or r.uploaded_by=?)
                order by r.received_at desc, r.rfq_id desc
                """, (rs, row) -> new RfqDto.Summary(rs.getLong("rfq_id"), rs.getString("rfq_number"),
                rs.getString("company_name"), rs.getString("item"),
                RfqStatus.valueOf(rs.getString("status")), rs.getTimestamp("received_at").toLocalDateTime()),
                user.userType().name(), user.companyId(), mine, user.userId());
    }

    public RfqDto.Detail detail(Long id) {
        return jdbc.query("""
                select r.*, c.name as company_name,
                       (select t.task_id from ai_tasks t where t.rfq_number = r.rfq_number
                        order by t.created_at desc, t.task_id desc limit 1) as latest_task_id
                from rfqs r join companies c on c.company_id = r.customer_company_id
                where r.rfq_id = ?
                """, (rs, row) -> new RfqDto.Detail(rs.getLong("rfq_id"), rs.getString("rfq_number"),
                rs.getLong("customer_company_id"), rs.getString("company_name"), rs.getString("item"),
                RfqStatus.valueOf(rs.getString("status")), rs.getTimestamp("received_at").toLocalDateTime(),
                nullableInt(rs, "eau"), nullableInt(rs, "sample_qty"), date(rs, "sample_due_date"),
                date(rs, "sop_date"), nullableInt(rs, "target_price_krw"), date(rs, "quote_due_date"),
                rs.getObject("latest_task_id", UUID.class)), id).stream().findFirst()
                .orElseThrow(() -> missing(id.toString()));
    }

    public InternalRfqDto.RfqInfo internal(String number) {
        return jdbc.query("select * from rfqs where rfq_number = ?", (rs, row) ->
                new InternalRfqDto.RfqInfo(rs.getLong("rfq_id"), rs.getString("rfq_number"),
                        rs.getLong("customer_company_id"), rs.getString("item"),
                        RfqStatus.valueOf(rs.getString("status")), rs.getString("file_path"),
                        nullableInt(rs, "eau"), nullableInt(rs, "sample_qty"), date(rs, "sample_due_date"),
                        date(rs, "sop_date"), nullableInt(rs, "target_price_krw"), date(rs, "quote_due_date")),
                number).stream().findFirst().orElseThrow(() -> missing(number));
    }

    public String content(String number) {
        String relative = internal(number).filePath();
        if (relative == null || relative.isBlank()) throw missing(number + " 원문");
        Path file = filesDir.resolve(relative).normalize();
        if (!file.startsWith(filesDir) || !Files.isRegularFile(file)) throw missing(number + " 원문");
        try {
            if (!file.toRealPath().startsWith(filesDir.toRealPath())) throw missing(number + " 원문");
            return Files.readString(file);
        } catch (IOException e) {
            throw missing(number + " 원문");
        }
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        return (Integer) rs.getObject(column);
    }

    private java.time.LocalDate date(ResultSet rs, String column) throws SQLException {
        var value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private ApiException missing(String id) {
        return new ApiException(ErrorCode.NOT_FOUND, "없는 RFQ입니다: " + id);
    }
}
