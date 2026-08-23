package com.phonelm.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

@Entity
data class VectorEntity(
    @Id var id: Long = 0,
    var text: String = "",
    var fileName: String = "",
    var pageNumber: Int = 0,
    @HnswIndex(dimensions = 384, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null
)
