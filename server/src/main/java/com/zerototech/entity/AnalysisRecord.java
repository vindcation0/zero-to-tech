package com.zerototech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文字分析记录实体（MyBatis-Plus 规范）
 */
@TableName("analysis_record")
public class AnalysisRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String text;

    private String pinyin;

    private Double score;

    private String sentiment;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public AnalysisRecord() {
    }

    public AnalysisRecord(String text, String pinyin, Double score, String sentiment) {
        this.text = text;
        this.pinyin = pinyin;
        this.score = score;
        this.sentiment = sentiment;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getPinyin() {
        return pinyin;
    }

    public void setPinyin(String pinyin) {
        this.pinyin = pinyin;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
