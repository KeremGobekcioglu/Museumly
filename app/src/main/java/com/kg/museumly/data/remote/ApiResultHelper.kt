package com.kg.museumly.data.remote

import com.kg.museumly.domain.ApiResult
import retrofit2.HttpException

// A second attempt only makes sense when the failure was the server's,
// not the device's. Offline and timeouts fail again identically.
fun ApiResult.Failed.worthRetrying() : Boolean
{
    val e = cause
    if( e is HttpException)
    {
        return e.code() == 429 || e.code() in 500..599
    }
    return false
}