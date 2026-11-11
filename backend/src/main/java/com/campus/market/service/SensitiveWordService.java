package com.campus.market.service;

import com.campus.market.entity.SensitiveWord;
import com.campus.market.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感词过滤（BR-11，B0401）：启动时加载词表到内存 Set（词表小，02§2.7）。
 * 覆盖范围（BR-11）：留言、商品标题与描述、评价内容。
 */
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;
    private final Set<String> words = new HashSet<>();

    @PostConstruct
    public void load() {
        List<SensitiveWord> all = sensitiveWordMapper.selectList(null);
        all.forEach(w -> words.add(w.getWord().toLowerCase()));
    }

    /** BR-11：内容命中任一敏感词返回 true（忽略大小写） */
    public boolean hits(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase();
        return words.stream().anyMatch(lower::contains);
    }
}