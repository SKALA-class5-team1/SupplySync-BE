package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import com.skala.supplyquote.supplyquote_backend.internal.dto.CatalogDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogQueryService {
    private final JdbcTemplate jdbc;

    public CatalogQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CatalogDto.ModelResponse> models() {
        return jdbc.query("select * from models order by model_id", (rs, row) -> modelRow(rs));
    }

    public CatalogDto.ModelResponse model(String id) {
        return jdbc.query("select * from models where model_id = ?", (rs, row) -> modelRow(rs), id)
                .stream().findFirst().orElseThrow(() -> missing("모델", id));
    }

    private CatalogDto.ModelResponse modelRow(ResultSet rs) throws SQLException {
        String id = rs.getString("model_id");
        List<CatalogDto.OptionResponse> options = jdbc.query("""
                select o.* from model_options mo join options o on o.option_code = mo.option_code
                where mo.model_id = ? order by o.option_code
                """, (or, row) -> new CatalogDto.OptionResponse(or.getString("option_code"),
                or.getString("option_name"), or.getString("changes_field"), or.getString("new_value"),
                or.getBigDecimal("add_weight_kg"), or.getInt("add_noise_db"), or.getInt("add_price_krw"),
                or.getString("replaces_part_id"), or.getString("uses_part_id")), id);
        return new CatalogDto.ModelResponse(id, rs.getString("model_name"), rs.getBigDecimal("flow_lpm"),
                rs.getInt("head_kpa"), rs.getBigDecimal("voltage_min"), rs.getBigDecimal("voltage_max"),
                rs.getInt("temp_min"), rs.getInt("temp_max"), rs.getString("ip_rating"), rs.getInt("noise_db"),
                rs.getString("comm"), rs.getInt("size_l_mm"), rs.getInt("size_w_mm"), rs.getInt("size_h_mm"),
                rs.getBigDecimal("weight_kg"), rs.getInt("base_price_krw"), options);
    }

    public CatalogDto.BomResponse bom(String modelId, List<String> requested) {
        var model = model(modelId);
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (requested != null) {
            for (String value : requested) {
                for (String token : value.split(",", -1)) {
                    String code = token.trim();
                    if (code.isEmpty() || !selected.add(code)) {
                        throw new ApiException(ErrorCode.VALIDATION_ERROR, "옵션 코드가 비었거나 중복되었습니다.");
                    }
                }
            }
        }
        Map<String, CatalogDto.OptionResponse> allowed = new LinkedHashMap<>();
        model.options().forEach(option -> allowed.put(option.optionCode(), option));
        for (String code : selected) {
            if (!allowed.containsKey(code)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "모델에 적용할 수 없는 옵션입니다: " + code);
            }
        }
        LinkedHashMap<String, CatalogDto.BomLine> lines = new LinkedHashMap<>();
        jdbc.query("""
                select b.part_id, p.part_name, b.qty_per_unit from bom b
                join parts p on p.part_id = b.part_id where b.model_id = ? order by b.part_id
                """, rs -> {
            String id = rs.getString("part_id");
            lines.put(id, new CatalogDto.BomLine(id, rs.getString("part_name"), rs.getInt("qty_per_unit"), "BASE", null));
        }, modelId);
        for (String code : selected) {
            var option = allowed.get(code);
            if (option.replacesPartId() == null && option.usesPartId() == null) continue;
            if (option.replacesPartId() == null || option.usesPartId() == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "옵션의 BOM 교체 정보가 불완전합니다: " + code);
            }
            var replaced = lines.remove(option.replacesPartId());
            if (replaced == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "교체할 BOM 부품이 없습니다: " + option.replacesPartId());
            }
            var used = part(option.usesPartId());
            var existing = lines.get(used.partId());
            int qty = replaced.qtyPerUnit() + (existing == null ? 0 : existing.qtyPerUnit());
            lines.put(used.partId(), new CatalogDto.BomLine(used.partId(), used.partName(), qty,
                    "OPTION:" + code, replaced.partId()));
        }
        return new CatalogDto.BomResponse(modelId, new ArrayList<>(selected), new ArrayList<>(lines.values()));
    }

    public CatalogDto.PartResponse part(String id) {
        return jdbc.query("select * from parts where part_id = ?", (rs, row) ->
                new CatalogDto.PartResponse(rs.getString("part_id"), rs.getString("part_name"),
                        rs.getString("supplier"), rs.getInt("unit_cost_krw"), rs.getInt("lead_time_days"),
                        rs.getBoolean("is_critical")), id).stream().findFirst().orElseThrow(() -> missing("부품", id));
    }

    public CatalogDto.InventoryResponse inventory(String id) {
        return jdbc.query("select * from inventory where part_id = ?", (rs, row) ->
                new CatalogDto.InventoryResponse(id, rs.getInt("on_hand"), rs.getInt("allocated"),
                        rs.getInt("on_hand") - rs.getInt("allocated"), rs.getInt("incoming_qty"),
                        rs.getDate("incoming_date") == null ? null : rs.getDate("incoming_date").toLocalDate()),
                id).stream().findFirst().orElseThrow(() -> missing("재고", id));
    }

    public List<CatalogDto.SubstituteResponse> substitutes(String id) {
        part(id);
        return jdbc.query("""
                select ps.part_id, ps.substitute_part_id, ps.validation_days, ps.pin_compatible,
                       ps.ref_chunk_id, p.part_name, p.supplier,
                       p.unit_cost_krw - original.unit_cost_krw as unit_cost_diff_krw,
                       coalesce(i.on_hand - i.allocated, 0) as available_qty
                from part_substitutes ps join parts original on original.part_id = ps.part_id
                join parts p on p.part_id = ps.substitute_part_id
                left join inventory i on i.part_id = p.part_id
                where ps.part_id = ? order by ps.substitute_part_id
                """, (rs, row) -> new CatalogDto.SubstituteResponse(id, rs.getString("substitute_part_id"),
                rs.getString("part_name"), rs.getString("supplier"), rs.getInt("validation_days"),
                rs.getBoolean("pin_compatible"), rs.getString("ref_chunk_id"),
                rs.getInt("unit_cost_diff_krw"), rs.getInt("available_qty")), id);
    }

    private ApiException missing(String type, String id) {
        return new ApiException(ErrorCode.NOT_FOUND, "없는 " + type + "입니다: " + id);
    }
}
