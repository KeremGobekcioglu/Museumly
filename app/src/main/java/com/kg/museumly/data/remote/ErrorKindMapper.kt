package com.kg.museumly.data.remote

import com.kg.museumly.domain.ApiResult
import com.kg.museumly.domain.ErrorKind
import java.io.IOException

fun ApiResult.Failed.toErrorKind(): ErrorKind =
    if (cause is IOException) ErrorKind.NETWORK else ErrorKind.UNKNOWN