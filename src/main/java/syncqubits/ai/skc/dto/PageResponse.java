package syncqubits.ai.skc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {
    private int page;
    private int size;
    private long total;
    private List<T> items;

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return PageResponse.<T>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .items(page.getContent().stream().map(mapper).toList())
                .build();
    }
}
