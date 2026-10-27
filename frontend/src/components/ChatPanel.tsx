import { useCallback, useEffect, useRef, useState } from 'react'
import { SUGGESTIONS } from '../data/schemes'
import { Markdown } from '../lib/markdown'
import type { BackendState, ChatMessage } from '../lib/types'
import {
  AlertIcon,
  CapIcon,
  CopyIcon,
  PlusIcon,
  RefreshIcon,
  SendIcon,
  ShieldIcon,
  SparkIcon,
} from './Icons'

export interface Draft {
  text: string
  nonce: number
}

interface ChatPanelProps {
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
}

interface TypewriterProps {
  text: string
  animate: boolean
  onDone: () => void
  onGrow?: () => void
}

const CHARS_PER_SECOND = 900

function Typewriter({ text, animate, onDone, onGrow }: TypewriterProps) {
  const [shown, setShown] = useState(animate ? 0 : text.length)
  const doneRef = useRef(onDone)
  const growRef = useRef(onGrow)
  doneRef.current = onDone
  growRef.current = onGrow

  useEffect(() => {
    if (!animate) {
      setShown(text.length)
      return
    }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setShown(text.length)
      doneRef.current()
      return
    }

    let frame = 0
    let last = performance.now()
    let position = 0

    const step = (now: number) => {
      const delta = Math.min(120, now - last)
      last = now
      position = Math.min(text.length, position + Math.max(1, Math.round((delta / 1000) * CHARS_PER_SECOND)))
      setShown(position)
      growRef.current?.()
      if (position < text.length) {
        frame = requestAnimationFrame(step)
      } else {
        doneRef.current()
      }
    }

    frame = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame)
  }, [text, animate])

  return (
    <>
      <Markdown text={text.slice(0, shown)} />
      {shown < text.length && <span className="caret" aria-hidden="true" />}
    </>
  )
}

interface MessageRowProps {
  message: ChatMessage
  onRetry: (query: string) => void
  onFinishStreaming: (id: string) => void
  onGrow: () => void
}

function MessageRow({ message, onRetry, onFinishStreaming, onGrow }: MessageRowProps) {
  const isUser = message.role === 'user'

  if (isUser) {
    return (
      <div className="msg msg-user">
        <div className="msg-stack">
          <span className="msg-label">You</span>
          <div className="bubble bubble-user">
            <p>{message.content}</p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="msg msg-assistant">
      <div className="msg-avatar" aria-hidden="true">
        <CapIcon size={16} />
      </div>
      <div className="msg-stack">
        <span className="msg-label">Assistant</span>
        <div className={`bubble bubble-assistant${message.error ? ' bubble-error' : ''}`}>
          {message.error ? (
            <>
              <p className="error-title">
                <AlertIcon size={15} />
                Couldn&rsquo;t complete the request
              </p>
              <p className="error-body">{message.content}</p>
              {message.retryQuery && (
                <button type="button" className="btn btn-outline btn-sm" onClick={() => onRetry(message.retryQuery!)}>
                  <RefreshIcon size={15} />
                  Retry
                </button>
              )}
            </>
          ) : message.content === '' && message.streaming ? (
            <div className="typing" role="status" aria-label="Assistant is preparing an answer">
              <span />
              <span />
              <span />
              <em>Checking scheme guidelines?</em>
            </div>
          ) : (
            <div className="bubble-text">
              <Typewriter
                text={message.content}
                animate={Boolean(message.streaming)}
                onDone={() => onFinishStreaming(message.id)}
                onGrow={onGrow}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export function ChatPanel({
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
}: ChatPanelProps) {
  const [value, setValue] = useState('')
  const [copied, setCopied] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const stickyRef = useRef(true)
  const lastGrowRef = useRef(0)

  const scrollToEnd = useCallback((smooth = false) => {
    const element = listRef.current
    if (!element) return
    element.scrollTo({ top: element.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  }, [])

  const handleScroll = () => {
    const element = listRef.current
    if (!element) return
    stickyRef.current = element.scrollHeight - element.scrollTop - element.clientHeight < 96
  }

  const handleGrow = useCallback(() => {
    const now = performance.now()
    if (now - lastGrowRef.current < 50) return
    lastGrowRef.current = now
    if (stickyRef.current) scrollToEnd()
  }, [scrollToEnd])

  useEffect(() => {
    stickyRef.current = true
    scrollToEnd()
  }, [messages.length, scrollToEnd])

  useEffect(() => {
    if (!draft.nonce) return
    setValue(draft.text)
    const element = textareaRef.current
    if (element) {
      element.focus()
      element.setSelectionRange(element.value.length, element.value.length)
    }
  }, [draft])

  useEffect(() => {
    const element = textareaRef.current
    if (!element) return
    element.style.height = '0px'
    element.style.height = `${Math.min(element.scrollHeight, 160)}px`
  }, [value])

  const submit = () => {
    const query = value.trim()
    if (!query || busy) return
    setValue('')
    onSend(query)
  }

  const copyId = async () => {
    try {
      await navigator.clipboard.writeText(conversationId)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1400)
    } catch {
      /* clipboard permission denied */
    }
  }

  return (
    <div className="chat-container">
      <header className="chat-header">
        <div className="chat-header-left">
          <div className="chat-avatar">
            <SparkIcon size={20} />
          </div>
          <div className="chat-header-info">
            <h2 className="chat-title">Scholarship Assistant</h2>
            <div className="chat-status-text">
              <span className={`status-indicator-dot status-${backendState}`} aria-hidden="true" />
              <span>
                {backendState === 'online'
                  ? 'Online ? Grounded in official guidelines'
                  : backendState === 'connecting'
                    ? 'Connecting...'
                    : 'Offline'}
              </span>
            </div>
          </div>
        </div>
        <div className="chat-header-right">
          {conversationId && (
            <button
              type="button"
              className="conversation-id-btn"
              onClick={copyId}
              title="Copy conversation ID"
              aria-label="Copy conversation ID"
            >
              <CopyIcon size={14} />
              <span>{copied ? 'Copied!' : `ID: ${conversationId.slice(0, 8)}`}</span>
            </button>
          )}
          <button
            type="button"
            className="btn-icon"
            onClick={onNewChat}
            title="Start new chat"
            aria-label="Start new chat"
            disabled={busy}
          >
            <PlusIcon size={18} />
          </button>
        </div>
      </header>

      {backendState === 'offline' && (
        <div className="offline-banner" role="alert">
          <span>
            <AlertIcon size={15} /> The assistant backend is unreachable. Start Spring Boot on port
            8080 and retry.
          </span>
          <button type="button" className="btn btn-outline btn-sm" onClick={onRetryConnection}>
            <RefreshIcon size={15} />
            Retry connection
          </button>
        </div>
      )}

      <div className="messages" ref={listRef} onScroll={handleScroll} role="log" aria-live="polite">
                {messages.length === 0 ? (
          <div className="welcome">
            <div className="welcome-header">
              <div className="welcome-avatar"><SparkIcon size={22} /></div>
              <div>
                <h2 className="welcome-title">Scholarship Assistant</h2>
                <p className="welcome-subtitle">Find scholarships you may be eligible for</p>
              </div>
            </div>
            <p className="welcome-intro">Hi! I can help you find scholarships and understand their eligibility criteria. Tell me about your education, family income, category, and state, or ask about a specific scheme.</p>
            <div className="quick-actions">
              <button type="button" className="quick-card" onClick={() => onSend('Check my eligibility for scholarships based on my profile details')}>
                <span className="quick-icon"><ShieldIcon size={18} /></span>
                <div>
                  <p className="quick-title">Check my eligibility</p>
                  <p className="quick-desc">Share your details to find matching schemes</p>
                </div>
              </button>
              <button type="button" className="quick-card" onClick={() => onSend('Find scholarships I might be eligible for')}>
                <span className="quick-icon"><SparkIcon size={18} /></span>
                <div>
                  <p className="quick-title">Find scholarships</p>
                  <p className="quick-desc">Get personalized recommendations</p>
                </div>
              </button>
              <button type="button" className="quick-card" onClick={() => document.getElementById('schemes')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>
                <span className="quick-icon"><CapIcon size={18} /></span>
                <div>
                  <p className="quick-title">Explore schemes</p>
                  <p className="quick-desc">Browse all available schemes</p>
                </div>
              </button>
            </div>
            <div className="suggestion-row">
              <p className="suggestion-label">Try asking</p>
              <div className="suggestion-chips">
                {SUGGESTIONS.slice(0, 3).map((sug) => (
                  <button type="button" className="suggestion-chip" key={sug} onClick={() => onSend(sug)} disabled={busy}>{sug}</button>
                ))}
              </div>
            </div>
          </div>
        ) : (
          messages.map((message) => (
            <MessageRow
              key={message.id}
              message={message}
              onRetry={onRetry}
              onFinishStreaming={onFinishStreaming}
              onGrow={handleGrow}
            />
          ))
        )}
      </div>

      <div className="composer-wrap">
        <form
          className="composer"
          onSubmit={(event) => {
            event.preventDefault()
            submit()
          }}
        >
          <div className="composer-inner">
            <textarea
              ref={textareaRef}
              value={value}
              rows={1}
              placeholder="Ask anything about scholarships..."
              aria-label="Message the scholarship assistant"
              spellCheck={false}
              onChange={(event) => setValue(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  submit()
                }
              }}
            />
            <button
              type="submit"
              className="composer-send"
              disabled={!value.trim() || busy}
              aria-label="Send message"
              title="Send message"
            >
              <SendIcon size={20} />
            </button>
          </div>
        </form>
        <p className="composer-meta">Scholarship Assistant can make mistakes. Please verify important details.</p>
      </div>

      <p className="chat-disclaimer">
        <ShieldIcon size={13} />
        Advisory only. Verify scheme details on the National Scholarship Portal before applying.
      </p>
    </div>
  )
}
