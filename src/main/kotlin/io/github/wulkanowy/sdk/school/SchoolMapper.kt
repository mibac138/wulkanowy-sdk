package io.github.wulkanowy.sdk.school

import io.github.wulkanowy.sdk.pojo.School
import io.github.wulkanowy.api.school.School as ScrapperSchool

fun ScrapperSchool.mapSchool(): School {
    return School(
        name = name,
        address = address,
        contact = contact,
        headmaster = headmaster,
        pedagogue = pedagogue
    )
}
