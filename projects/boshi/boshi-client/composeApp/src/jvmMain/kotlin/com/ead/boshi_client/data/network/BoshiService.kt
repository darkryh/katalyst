package com.ead.boshi_client.data.network

import com.ead.boshi_client.data.network.models.EmailDto
import com.ead.boshi_client.data.network.models.EmailStatusResponse
import com.ead.boshi_client.data.network.models.SendEmailRequest
import com.ead.boshi_client.data.network.models.SendEmailResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BoshiService {
    @POST("emails/send")
    suspend fun sendEmail(@Body request: SendEmailRequest): SendEmailResponse

    @GET("emails/{messageId}/status")
    suspend fun getEmailStatus(@Path("messageId") messageId: String): EmailStatusResponse

    @GET("emails")
    suspend fun getEmails(@Query("page") page: Int, @Query("limit") limit: Int): List<EmailDto>
}
