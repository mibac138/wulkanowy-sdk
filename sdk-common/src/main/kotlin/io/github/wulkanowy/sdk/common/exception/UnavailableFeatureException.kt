package io.github.wulkanowy.sdk.common.exception

import java.io.IOException

class UnavailableFeatureException : IOException {
    constructor(message: String) : super(message)

    constructor() : super()
}
