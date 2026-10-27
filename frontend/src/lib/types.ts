export type Role = 'user' | 'assistant'

export interface ChatMessage {
  id: string
  role: Role
  content: string
  streaming?: boolean
  error?: boolean
  retryQuery?: string
}

export type BackendState = 'connecting' | 'online' | 'offline'
