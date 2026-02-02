package com.newsthread.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = CachedArticleEntity::class,
            parentColumns = ["url"],
            childColumns = ["articleUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["articleUrl"], unique = true),
        Index(value = ["computedAt"])
    ]
)
data class ArticleEmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val articleUrl: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray,
    val embeddingModel: String,     // e.g., "all-MiniLM-L6-v2"
    val dimensions: Int,            // e.g., 384
    val computedAt: Long,
    val expiresAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ArticleEmbeddingEntity
        return id == other.id && articleUrl == other.articleUrl
    }

    override fun hashCode(): Int = 31 * id.hashCode() + articleUrl.hashCode()
}
