package com.skala.supplyquote.supplyquote_backend.service;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import com.skala.supplyquote.supplyquote_backend.domain.AlternativeType;
import com.skala.supplyquote.supplyquote_backend.domain.QuoteStatus;
import com.skala.supplyquote.supplyquote_backend.domain.ReviewStatus;
import com.skala.supplyquote.supplyquote_backend.dto.QuoteParts;
import com.skala.supplyquote.supplyquote_backend.dto.ReviewParts;
import com.skala.supplyquote.supplyquote_backend.internal.dto.QuoteInternalDto;
import com.skala.supplyquote.supplyquote_backend.internal.dto.ReviewUpsertDto;
import com.skala.supplyquote.supplyquote_backend.web.dto.AnalysisDto;
import com.skala.supplyquote.supplyquote_backend.web.dto.QuoteViews;
import com.skala.supplyquote.supplyquote_backend.web.dto.ReviewDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ReviewQuoteService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public ReviewQuoteService(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public ReviewUpsertDto.ReviewUpsertResponse upsert(ReviewUpsertDto.ReviewUpsertRequest r) {
        if (jdbc.queryForObject("select count(*) from rfqs where rfq_number=?", Integer.class, r.rfqNumber()) == 0)
            throw missing("RFQ", r.rfqNumber());
        jdbc.update("""
                insert into rfq_reviews(rfq_number,thread_id) values (?,?)
                on conflict (rfq_number,thread_id) do nothing
                """, r.rfqNumber(), r.threadId());
        Long id = jdbc.queryForObject("select review_id from rfq_reviews where rfq_number=? and thread_id=?",
                Long.class, r.rfqNumber(), r.threadId());
        if (r.status() != null) updateReview(id, "status", r.status().name());
        if (r.selectedModel() != null) updateReview(id, "selected_model", r.selectedModel());
        if (r.fitType() != null) updateReview(id, "fit_type", r.fitType());
        if (r.recommendedAlt() != null) updateReview(id, "recommended_alt", r.recommendedAlt().name());
        if (r.options() != null) updateArray("rfq_reviews", "options", "review_id", id, r.options());
        reviewJson(id, "issues", r.issues());
        reviewJson(id, "passed_checks", r.passedChecks());
        reviewJson(id, "assumptions", r.assumptions());
        reviewJson(id, "customer_questions", r.customerQuestions());
        reviewJson(id, "contract_risks", r.contractRisks());
        reviewJson(id, "shortages", r.shortages());
        reviewJson(id, "alternatives", r.alternatives());
        reviewJson(id, "final_plan", r.finalPlan());
        jdbc.update("update rfq_reviews set updated_at=now() where review_id=?", id);
        return jdbc.queryForObject("select status,updated_at from rfq_reviews where review_id=?", (rs, row) ->
                new ReviewUpsertDto.ReviewUpsertResponse(id, r.rfqNumber(), r.threadId(),
                        ReviewStatus.valueOf(rs.getString("status")), rs.getTimestamp("updated_at").toLocalDateTime()), id);
    }

    private void updateReview(Long id, String column, Object value) {
        jdbc.update("update rfq_reviews set " + column + "=? where review_id=?", value, id);
    }

    private void reviewJson(Long id, String column, Object value) {
        if (value != null) jdbc.update("update rfq_reviews set " + column + "=?::jsonb where review_id=?",
                json.writeValueAsString(value), id);
    }

    private void updateArray(String table, String column, String idColumn, Object id, List<String> values) {
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("update " + table + " set " + column + "=? where " + idColumn + "=?");
            ps.setArray(1, connection.createArrayOf("text", values.toArray(String[]::new)));
            ps.setObject(2, id);
            return ps;
        });
    }

    @Transactional
    public QuoteInternalDto.QuoteCreated create(QuoteInternalDto.QuoteDraftRequest r) {
        Long reviewId = jdbc.query("select review_id from rfq_reviews where thread_id=? and rfq_number=? for update",
                (rs, row) -> rs.getLong(1), r.threadId(), r.rfqNumber()).stream().findFirst()
                .orElseThrow(() -> missing("분석 결과", r.threadId()));
        if (r.revision() == null || r.revision() < 1 || r.revision() > 4)
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "revision은 1부터 4까지입니다 (수정 최대 3회).");
        var previousAttempt = jdbc.query("select quote_id,status from quotes where review_id=? and revision=?",
                (rs, row) -> new QuoteInternalDto.QuoteCreated(rs.getString("quote_id"),
                        QuoteStatus.valueOf(rs.getString("status")), reviewId), reviewId, r.revision());
        if (!previousAttempt.isEmpty()) return previousAttempt.getFirst();
        if (r.revision() == 1) {
            Integer count = jdbc.queryForObject("select count(*) from quotes where review_id=?", Integer.class, reviewId);
            if (count != null && count > 0) throw conflict("첫 견적은 이미 저장되었습니다.");
        } else {
            var previous = jdbc.query("select revision_requested, status from quotes where review_id=? and revision=?",
                    (rs, row) -> Map.entry(rs.getBoolean(1), rs.getString(2)), reviewId, r.revision() - 1)
                    .stream().findFirst().orElse(null);
            boolean revised = previous != null && previous.getKey();
            // SG4 재검증에서 재고가 바뀐 경우: 승인된 직전 견적을 폐기하고 새 revision으로 다시 쓴다
            boolean staleAfterRevalidation = previous != null && "APPROVED".equals(previous.getValue())
                    && jdbc.queryForObject("select count(*) from ai_tasks where thread_id=? and status='REVALIDATING'",
                    Integer.class, r.threadId()) > 0;
            if (!revised && !staleAfterRevalidation)
                throw conflict("REVISE 승인 후 다음 revision을 저장할 수 있습니다.");
            if (staleAfterRevalidation) {
                jdbc.update("update quotes set status='REJECTED', reject_reason='재고 변경으로 재작성', revision_requested=true "
                        + "where review_id=? and revision=? and status='APPROVED'", reviewId, r.revision() - 1);
                jdbc.update("update ai_tasks set status='RUNNING', current_step='QUOTE', updated_at=now() "
                        + "where thread_id=? and status='REVALIDATING'", r.threadId());
            }
        }
        if (jdbc.queryForObject("select count(*) from models where model_id=?", Integer.class, r.modelId()) == 0)
            throw missing("모델", r.modelId());
        LocalDate issueDate = r.issueDate() == null ? LocalDate.now() : r.issueDate();
        Integer seq = jdbc.queryForObject("""
                insert into quote_daily_seq(day,last_seq) values (?,1)
                on conflict (day) do update set last_seq=quote_daily_seq.last_seq+1
                returning last_seq
                """, Integer.class, issueDate);
        String quoteId = "Q-" + issueDate.format(DateTimeFormatter.ofPattern("yyyy-MMdd")) + "-" + String.format("%03d", seq);
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    insert into quotes(quote_id,review_id,rfq_number,model_id,options,unit_price_krw,
                                       parts_cost_krw,margin_pct,price_by_year,sample_qty,sample_due_date,
                                       valid_until,conditions,checks,status,issue_date,details,revision)
                    values (?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?::jsonb,?::jsonb,'DRAFT',?,?::jsonb,?)
                    """);
            ps.setString(1, quoteId);
            ps.setLong(2, reviewId);
            ps.setString(3, r.rfqNumber());
            ps.setString(4, r.modelId());
            ps.setArray(5, connection.createArrayOf("text", r.options() == null ? new String[0] : r.options().toArray(String[]::new)));
            ps.setInt(6, r.unitPriceKrw());
            ps.setObject(7, r.partsCostKrw());
            ps.setObject(8, r.marginPct());
            ps.setString(9, json.writeValueAsString(r.priceByYear()));
            ps.setObject(10, r.sampleQty());
            ps.setObject(11, r.sampleDueDate());
            ps.setObject(12, r.validUntil());
            ps.setString(13, r.conditions() == null ? null : json.writeValueAsString(r.conditions()));
            ps.setString(14, r.checks() == null ? null : json.writeValueAsString(r.checks()));
            ps.setObject(15, issueDate);
            ps.setString(16, json.writeValueAsString(new QuoteParts.ManagerDetails(
                    r.recipient(), r.incoterms(), r.priceBreakdown(), r.partsCostBreakdown())));
            ps.setInt(17, r.revision());
            return ps;
        });
        return new QuoteInternalDto.QuoteCreated(quoteId, QuoteStatus.DRAFT, reviewId);
    }

    @Transactional
    public QuoteInternalDto.QuoteCreated patch(String quoteId, QuoteInternalDto.QuotePatchRequest r) {
        var found = quoteStatus(quoteId);
        if (found.status() != QuoteStatus.DRAFT) throw conflict("DRAFT 견적만 수정할 수 있습니다.");
        if (r.checks() != null) jdbc.update("update quotes set checks=?::jsonb where quote_id=?", json.writeValueAsString(r.checks()), quoteId);
        if (r.conditions() != null) jdbc.update("update quotes set conditions=?::jsonb where quote_id=?", json.writeValueAsString(r.conditions()), quoteId);
        return found;
    }

    @Transactional
    public QuoteInternalDto.RegisterResponse register(String quoteId) {
        var found = quoteStatus(quoteId);
        if (found.status() == QuoteStatus.REGISTERED)
            return new QuoteInternalDto.RegisterResponse(quoteId, QuoteStatus.REGISTERED,
                    com.skala.supplyquote.supplyquote_backend.domain.RfqStatus.QUOTED, "ALREADY_PROCESSED");
        if (found.status() != QuoteStatus.APPROVED) throw conflict("승인된 견적만 등록할 수 있습니다.");
        int changed = jdbc.update("update quotes set status='REGISTERED',registered_at=now() where quote_id=? and status='APPROVED'", quoteId);
        if (changed == 0) {
            if (quoteStatus(quoteId).status() == QuoteStatus.REGISTERED)
                return new QuoteInternalDto.RegisterResponse(quoteId, QuoteStatus.REGISTERED,
                        com.skala.supplyquote.supplyquote_backend.domain.RfqStatus.QUOTED, "ALREADY_PROCESSED");
            throw conflict("승인된 견적만 등록할 수 있습니다.");
        }
        String number = jdbc.queryForObject("select rfq_number from quotes where quote_id=?", String.class, quoteId);
        jdbc.update("update rfqs set status='QUOTED' where rfq_number=?", number);
        jdbc.update("""
                update ai_tasks set status='REGISTERING',current_step='REGISTRATION',updated_at=now()
                where thread_id=(select r.thread_id from quotes q join rfq_reviews r on r.review_id=q.review_id
                                 where q.quote_id=?)
                  and status in ('REGISTERING','REVALIDATING','WAITING_APPROVAL')
                """, quoteId);
        return new QuoteInternalDto.RegisterResponse(quoteId, QuoteStatus.REGISTERED,
                com.skala.supplyquote.supplyquote_backend.domain.RfqStatus.QUOTED, "CREATED");
    }

    public QuoteInternalDto.QuoteCreated quoteStatus(String id) {
        return jdbc.query("select status,review_id from quotes where quote_id=?", (rs, row) ->
                new QuoteInternalDto.QuoteCreated(id, QuoteStatus.valueOf(rs.getString("status")), rs.getLong("review_id")), id)
                .stream().findFirst().orElseThrow(() -> missing("견적", id));
    }

    public AnalysisDto.AnalysisView analysis(Long rfqId) {
        String number = jdbc.query("select rfq_number from rfqs where rfq_id=?", (rs, row) -> rs.getString(1), rfqId)
                .stream().findFirst().orElseThrow(() -> missing("RFQ", rfqId.toString()));
        ReviewDto.ManagerReview review = jdbc.query("select * from rfq_reviews where rfq_number=? order by updated_at desc, review_id desc limit 1",
                (rs, row) -> reviewRow(rs), number).stream().findFirst()
                .orElseThrow(() -> missing("분석 결과", number));
        return new AnalysisDto.AnalysisView(review, jdbc.query("select * from quotes where rfq_number=? order by created_at desc, quote_id desc",
                (rs, row) -> managerQuote(rs), number));
    }

    public List<QuoteViews.CustomerQuote> customerQuotes(Long rfqId) {
        String number = jdbc.query("select rfq_number from rfqs where rfq_id=?", (rs, row) -> rs.getString(1), rfqId)
                .stream().findFirst().orElseThrow(() -> missing("RFQ", rfqId.toString()));
        return jdbc.query("select * from quotes where rfq_number=? and status='REGISTERED' order by registered_at desc,quote_id desc",
                (rs, row) -> customerQuote(rs), number);
    }

    private ReviewDto.ManagerReview reviewRow(ResultSet rs) throws SQLException {
        return new ReviewDto.ManagerReview(rs.getString("rfq_number"), rs.getString("thread_id"),
                ReviewStatus.valueOf(rs.getString("status")), rs.getString("selected_model"), strings(rs.getArray("options")),
                rs.getString("fit_type"), values(rs.getString("issues"), ReviewParts.Issue.class),
                values(rs.getString("passed_checks"), String.class), values(rs.getString("assumptions"), ReviewParts.Assumption.class),
                (Long) rs.getObject("confirmed_by"), dateTime(rs, "confirmed_at"),
                values(rs.getString("customer_questions"), String.class), values(rs.getString("contract_risks"), ReviewParts.ContractRisk.class),
                values(rs.getString("shortages"), ReviewParts.Shortage.class), values(rs.getString("alternatives"), ReviewParts.Alternative.class),
                rs.getString("recommended_alt") == null ? null : AlternativeType.valueOf(rs.getString("recommended_alt")),
                finalPlan(rs.getString("final_plan")));
    }

    private QuoteViews.ManagerQuote managerQuote(ResultSet rs) throws SQLException {
        return new QuoteViews.ManagerQuote(rs.getString("quote_id"), rs.getString("rfq_number"), rs.getString("model_id"),
                strings(rs.getArray("options")), rs.getInt("unit_price_krw"), (Integer) rs.getObject("parts_cost_krw"),
                rs.getObject("margin_pct") == null ? null : rs.getDouble("margin_pct"),
                values(rs.getString("price_by_year"), QuoteParts.PriceByYear.class), (Integer) rs.getObject("sample_qty"),
                date(rs, "sample_due_date"), date(rs, "valid_until"), values(rs.getString("conditions"), QuoteParts.Condition.class),
                values(rs.getString("checks"), QuoteParts.Check.class), QuoteStatus.valueOf(rs.getString("status")),
                (Long) rs.getObject("approved_by"), dateTime(rs, "approved_at"), rs.getString("reject_reason"),
                dateTime(rs, "registered_at"), dateTime(rs, "created_at"), date(rs, "issue_date"),
                managerDetails(rs.getString("details")));
    }

    private QuoteViews.CustomerQuote customerQuote(ResultSet rs) throws SQLException {
        List<QuoteParts.CustomerPriceByYear> years = values(rs.getString("price_by_year"), QuoteParts.PriceByYear.class)
                .stream().map(p -> new QuoteParts.CustomerPriceByYear(p.year(), p.unitPriceKrw())).toList();
        QuoteParts.ManagerDetails details = managerDetails(rs.getString("details"));
        return new QuoteViews.CustomerQuote(rs.getString("quote_id"), rs.getString("rfq_number"), rs.getString("model_id"),
                strings(rs.getArray("options")), rs.getInt("unit_price_krw"), years, (Integer) rs.getObject("sample_qty"),
                date(rs, "sample_due_date"), date(rs, "valid_until"), values(rs.getString("conditions"), QuoteParts.Condition.class),
                QuoteStatus.REGISTERED, date(rs, "issue_date"), details == null ? null
                        : new QuoteParts.CustomerDetails(details.recipient(), details.incoterms(), details.priceBreakdown()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> finalPlan(String content) {
        return content == null ? null : json.readValue(content, Map.class);
    }

    private QuoteParts.ManagerDetails managerDetails(String content) {
        return content == null ? null : json.readValue(content, QuoteParts.ManagerDetails.class);
    }

    private <T> List<T> values(String content, Class<T> type) {
        if (content == null) return List.of();
        List<T> result = new ArrayList<>();
        for (JsonNode node : json.readTree(content)) result.add(json.treeToValue(node, type));
        return result;
    }

    private List<String> strings(java.sql.Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    private LocalDate date(ResultSet rs, String name) throws SQLException {
        var value = rs.getDate(name);
        return value == null ? null : value.toLocalDate();
    }

    private LocalDateTime dateTime(ResultSet rs, String name) throws SQLException {
        var value = rs.getTimestamp(name);
        return value == null ? null : value.toLocalDateTime();
    }

    private ApiException missing(String type, String id) { return new ApiException(ErrorCode.NOT_FOUND, "없는 " + type + "입니다: " + id); }
    private ApiException conflict(String message) { return new ApiException(ErrorCode.CONFLICT_STATE, message); }
}
