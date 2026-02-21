import { baseApi } from "@/app/baseApi"
import type { ContactEmailRequest, ContactEmailResponse } from "@/types/api"

export const contactApiSlice = baseApi.injectEndpoints({
  endpoints: build => ({
    sendContactEmail: build.mutation<ContactEmailResponse, ContactEmailRequest>({
      query: body => ({
        url: "/email/send",
        method: "POST",
        body,
      }),
    }),
  }),
})

export const { useSendContactEmailMutation } = contactApiSlice
