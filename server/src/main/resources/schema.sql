CREATE TABLE IF NOT EXISTS `analysis_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` VARCHAR(64) NOT NULL DEFAULT 'anonymous' COMMENT '用户唯一标识UUID',
  `text` TEXT NOT NULL COMMENT '原文',
  `pinyin` TEXT COMMENT '拼音',
  `score` DOUBLE NOT NULL COMMENT '情感分数',
  `sentiment` VARCHAR(64) DEFAULT NULL COMMENT '情感判断标签',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录生成时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文字实验室分析历史记录表';
