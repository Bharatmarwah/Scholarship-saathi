import { useCallback, useEffect, useRef, useState } from 'react'
import type { Draft } from './components/ChatPanel'
import { Footer } from './components/Footer'
import { Header } from './components/Header'
import { ApiError, describeError, fetchConversationId, sendChat } from './lib/api'
import type { BackendState, ChatMessage } from './lib/types'
import { AssistantPage } from './pages/AssistantPage'
import { Home } from './pages/Home'

const CONVERSATION_KEY = 'srs.conversationId'
const THEME_KEY = 'srs.theme'
const messagesKey = (id: string) => `srs.messages.${id}`

function readStoredMessages(id: string): ChatMessage[] {
  try {
    const raw = localStorage.getItem(messagesKey(id))
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(
        (item): item is ChatMessage =>
          Boolean(item) &&
          typeof item === 'object' &&
          typeof (item as ChatMessage).content === 'string' &&
          ((item as ChatMessage).role === 'user' || (item as ChatMessage).role === 'assistant'),
      )
      .map((item) => ({ ...item, streaming: false }))
  } catch {
    return []
  }
}

export default function App() {
  const [theme, setTheme] = useState<'light' | 'dark'>(() =>
    localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light',
  )
  const [conversationId, setConversationId] = useState('')
  const [backendState, setBackendState] = useState<BackendState>('connecting')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [busy, setBusy] = useState(false)
  const [draft, setDraft] = useState<Draft>({ text: '', nonce: 0 })
  const [view, setView] = useState<'home' | 'assistant'>('home')

  const conversationRef = useRef('')
  const busyRef = useRef(false)
  conversationRef.current = conversationId

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem(THEME_KEY, theme)
  }, [theme])

  const startConversation = useCallback(async () => {
    setBackendState('connecting')
    try {
      const id = await fetchConversationId()
      localStorage.setItem(CONVERSATION_KEY, id)
      setConversationId(id)
      setBackendState('online')
      return id
    } catch {
      const fallback = localStorage.getItem(CONVERSATION_KEY) || crypto.randomUUID()
      localStorage.setItem(CONVERSATION_KEY, fallback)
      setConversationId(fallback)
      setBackendState('offline')
      return fallback
    }
  }, [])

  useEffect(() => {
    const stored = localStorage.getItem(CONVERSATION_KEY)
    if (stored) {
      setConversationId(stored)
      fetchConversationId()
        .then(() => setBackendState('online'))
        .catch(() => setBackendState('offline'))
      return
    }
    void startConversation()
  }, [startConversation])

  useEffect(() => {
    if (!conversationId) return
    setMessages(readStoredMessages(conversationId))
  }, [conversationId])

  useEffect(() => {
    if (!conversationId) return
    const transcript = messages
      .filter((message) => !message.error)
      .map(({ id, role, content }) => ({ id, role, content }))
    try {
      localStorage.setItem(messagesKey(conversationId), JSON.stringify(transcript.slice(-60)))
    } catch {
      /* ignore */
    }
  }, [messages, conversationId])

  const ensureConversationId = useCallback(() => {
    if (conversationRef.current) return conversationRef.current
    const generated = crypto.randomUUID()
    localStorage.setItem(CONVERSATION_KEY, generated)
    setConversationId(generated)
    return generated
  }, [])

  const send = useCallback(
    async (text: string, addUser = true) => {
      const query = text.trim()
      if (!query || busyRef.current) return
      setView('assistant')
      const id = ensureConversationId()
      busyRef.current = true
      setBusy(true)

      const pendingId = crypto.randomUUID()
      setMessages((previous) => [
        ...previous,
        ...(addUser ? [{ id: crypto.randomUUID(), role: 'user' as const, content: query }] : []),
        { id: pendingId, role: 'assistant' as const, content: '', streaming: true },
      ])

      try {
        const result = await sendChat(id, query)
        setBackendState('online')
        setMessages((previous) =>
          previous.map((message) =>
            message.id === pendingId
              ? {
                  ...message,
                  content: result.response?.trim()
                    ? result.response
                    : 'I could not produce an answer for that question. Please try rephrasing it.',
                  retryQuery: undefined,
                }
              : message,
          ),
        )
      } catch (error) {
        const offline = error instanceof ApiError && error.status === 0
        setBackendState(offline ? 'offline' : 'online')
        setMessages((previous) =>
          previous.map((message) =>
            message.id === pendingId
              ? {
                  ...message,
                  streaming: false,
                  error: true,
                  content: describeError(error),
                  retryQuery: query,
                }
              : message,
          ),
        )
      } finally {
        busyRef.current = false
        setBusy(false)
      }
    },
    [ensureConversationId],
  )

  const retry = useCallback(
    (query: string) => {
      setMessages((previous) => previous.filter((message) => !message.error))
      void send(query, false)
    },
    [send],
  )

  const finishStreaming = useCallback((id: string) => {
    setMessages((previous) =>
      previous.map((message) =>
        message.id === id && message.streaming ? { ...message, streaming: false } : message,
      ),
    )
  }, [])

  const handleNewChat = useCallback(async () => {
    if (busyRef.current) return
    const previousId = conversationRef.current
    if (previousId) localStorage.removeItem(messagesKey(previousId))
    setMessages([])
    const id = await startConversation()
    setConversationId(id)
    setView('assistant')
  }, [startConversation])

  const retryConnection = useCallback(async () => {
    setBackendState('connecting')
    try {
      await fetchConversationId()
      setBackendState('online')
    } catch {
      setBackendState('offline')
    }
  }, [])

  const prefill = useCallback((text: string) => {
    setDraft((previous) => ({ text, nonce: previous.nonce + 1 }))
    setView('assistant')
  }, [])

  const goHome = (query?: string) => {
    if (query) {
      void send(query)
      return
    }
    setView('home')
  }

  return (
    <div className="app">
      <Header
        theme={theme}
        backendState={backendState}
        onToggleTheme={() => setTheme((current) => (current === 'light' ? 'dark' : 'light'))}
        onNewChat={() => void handleNewChat()}
      />
      <main>
        {view === 'home' ? (
          <Home onStart={goHome} />
        ) : (
          <AssistantPage
            conversationId={conversationId}
            backendState={backendState}
            messages={messages}
            busy={busy}
            draft={draft}
            onSend={(text) => void send(text)}
            onRetry={retry}
            onRetryConnection={() => void retryConnection()}
            onNewChat={() => void handleNewChat()}
            onFinishStreaming={finishStreaming}
            onPrefill={prefill}
          />
        )}
      </main>
      <Footer />
    </div>
  )
}
