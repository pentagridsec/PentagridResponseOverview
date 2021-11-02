package burp

import java.io.Serializable

data class HttpService(override val host: String, override val port: Int, override val protocol: String): IHttpService, Serializable{
    companion object{
        fun fromHttpService(s: IHttpService): HttpService{
            return HttpService(s.host, s.port, s.protocol)
        }
    }
}