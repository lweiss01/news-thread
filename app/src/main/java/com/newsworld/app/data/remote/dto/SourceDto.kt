package com.newsworld.app.data.remote.dto

import com.newsworld.app.domain.model.Source

data class SourceDto(
    val id: String?,
    val name: String?,
    val description: String?,
    val url: String?,
    val category: String?,
    val language: String?,
    val country: String?
)

fun SourceDto.toSource(): Source = Source(
    id = id,
    name = name ?: "Unknown",
    description = description,
    url = url,
    category = category,
    language = language,
    country = country
)
