import type { ReactNode } from 'react'

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = []
  const pattern =
    /(\*\*[^*]+\*\*|\*[^*\n]+\*|`[^`]+`|\[[0-9]+(?:\s*,\s*[0-9]+)*\]|\[[^\]]+\]\((?:https?:\/\/|\/)[^)\s]+\))/g
  let last = 0
  let match: RegExpExecArray | null
  let index = 0

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) nodes.push(text.slice(last, match.index))
    const token = match[0]
    const key = `${keyPrefix}-${index++}`

    if (token.startsWith('**')) {
      nodes.push(<strong key={key}>{token.slice(2, -2)}</strong>)
    } else if (token.startsWith('`')) {
      nodes.push(<code key={key}>{token.slice(1, -1)}</code>)
    } else if (token.startsWith('[') && token.includes('](')) {
      const link = /^\[([^\]]+)\]\(([^)\s]+)\)$/.exec(token)
      if (link) {
        nodes.push(
          <a key={key} href={link[2]} target="_blank" rel="noopener noreferrer">
            {link[1]}
          </a>,
        )
      } else {
        nodes.push(token)
      }
    } else if (/^\[\d/.test(token)) {
      nodes.push(
        <span className="cite" key={key} title={`Source reference ${token.slice(1, -1)}`}>
          {token.slice(1, -1)}
        </span>,
      )
    } else if (token.startsWith('*')) {
      nodes.push(<em key={key}>{token.slice(1, -1)}</em>)
    } else {
      nodes.push(token)
    }
    last = match.index + token.length
  }

  if (last < text.length) nodes.push(text.slice(last))
  return nodes
}

const BULLET = /^\s*[-•*]\s+(.*)$/
const ORDERED = /^\s*\d+[.)]\s+(.*)$/
const HEADING = /^\s*#{1,6}\s+(.*)$/

export function Markdown({ text }: { text: string }) {
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const blocks: ReactNode[] = []
  let i = 0
  let key = 0

  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) {
      i += 1
      continue
    }

    const bullet = BULLET.exec(line)
    if (bullet) {
      const items: string[] = []
      while (i < lines.length) {
        const current = BULLET.exec(lines[i])
        if (!current) break
        items.push(current[1])
        i += 1
      }
      const blockKey = key++
      blocks.push(
        <ul key={`ul-${blockKey}`}>
          {items.map((item, itemIndex) => (
            <li key={itemIndex}>{renderInline(item, `ul-${blockKey}-${itemIndex}`)}</li>
          ))}
        </ul>,
      )
      continue
    }

    const ordered = ORDERED.exec(line)
    if (ordered) {
      const items: string[] = []
      while (i < lines.length) {
        const current = ORDERED.exec(lines[i])
        if (!current) break
        items.push(current[1])
        i += 1
      }
      const blockKey = key++
      blocks.push(
        <ol key={`ol-${blockKey}`}>
          {items.map((item, itemIndex) => (
            <li key={itemIndex}>{renderInline(item, `ol-${blockKey}-${itemIndex}`)}</li>
          ))}
        </ol>,
      )
      continue
    }

    const heading = HEADING.exec(line)
    if (heading) {
      const blockKey = key++
      blocks.push(
        <p className="md-heading" key={`h-${blockKey}`}>
          {renderInline(heading[1], `h-${blockKey}`)}
        </p>,
      )
      i += 1
      continue
    }

    const paragraph: string[] = []
    while (
      i < lines.length &&
      lines[i].trim() &&
      !BULLET.test(lines[i]) &&
      !ORDERED.test(lines[i]) &&
      !HEADING.test(lines[i])
    ) {
      paragraph.push(lines[i])
      i += 1
    }
    const blockKey = key++
    blocks.push(<p key={`p-${blockKey}`}>{renderInline(paragraph.join(' '), `p-${blockKey}`)}</p>)
  }

  return <>{blocks}</>
}
