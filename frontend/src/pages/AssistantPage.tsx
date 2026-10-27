import { ChatPanel } from '../components/ChatPanel'
import type { Draft } from '../components/ChatPanel'
import { SchemesPanel } from '../components/SchemesPanel'
import type { BackendState, ChatMessage } from '../lib/types'

interface AssistantPageProps {
  conversationId: string
  backendState: BackendState
  messages: ChatMessage[]
  busy: boolean
  draft: Draft
  onSend: (text: string) => void
  onRetry: (query: string) => void
  onRetryConnection: () => void
  onNewChat: () => void
  onFinishStreaming: (id: string) => void
  onPrefill: (text: string) => void
}

export function AssistantPage({
  conversationId,
  backendState,
  messages,
  busy,
  draft,
  onSend,
  onRetry,
  onRetryConnection,
  onNewChat,
  onFinishStreaming,
  onPrefill,
}: AssistantPageProps) {
  return (
    <div className="assistant-page">
      <div className="assistant-page-header">
        <div className="container">
          <div className="assistant-page-header-inner">
            <div>
              <h1 className="assistant-page-title">Scholarship Assistant</h1>
              <p className="assistant-page-subtitle">AI-powered eligibility check for Central Government scholarships</p>
            </div>
          </div>
        </div>
      </div>
      <div className="container">
        <div className="assistant-page-layout">
          <ChatPanel
            conversationId={conversationId}
            backendState={backendState}
            messages={messages}
            busy={busy}
            draft={draft}
            onSend={onSend}
            onRetry={onRetry}
            onRetryConnection={onRetryConnection}
            onNewChat={onNewChat}
            onFinishStreaming={onFinishStreaming}
          />
          <SchemesPanel onPrefill={onPrefill} />
        </div>
      </div>
    </div>
  )
}
