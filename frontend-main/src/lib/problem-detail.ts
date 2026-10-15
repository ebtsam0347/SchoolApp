import { z } from 'zod'

// RFC 7807 ProblemDetail + our standard extensions. Shape locked by
// docs/api-conventions.md §3. `loose()` keeps domain-specific extensions
// (e.g. `conflictingRequestId`) on the parsed object — handlers that need
// them re-narrow with their own schema.

export const ViolationSchema = z.object({
  field: z.string(),
  code: z.string(),
  message: z.string(),
})

export type Violation = z.infer<typeof ViolationSchema>

export const ProblemDetailSchema = z
  .object({
    type: z.string(),
    title: z.string(),
    status: z.number().int(),
    detail: z.string().optional(),
    instance: z.string().optional(),
    code: z.string(),
    correlationId: z.string(),
    timestamp: z.string(),
    violations: z.array(ViolationSchema).optional(),
  })
  .loose()

export type ProblemDetail = z.infer<typeof ProblemDetailSchema>
