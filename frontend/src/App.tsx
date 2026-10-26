import { useEffect, useState } from 'react'
import { fetchHealth } from './api/health.ts'

type ServerState = 'checking' | 'ok' | 'error'

const MESSAGE: Record<ServerState, string> = {
  checking: '서버에 연결하는 중이에요',
  ok: '서버와 연결됐어요',
  error: '서버에 연결하지 못했어요. backend가 켜져 있는지 확인해 주세요',
}

const STATUS_COLOR: Record<ServerState, string> = {
  checking: 'text-muted',
  ok: 'text-good',
  error: 'text-bad',
}

// 첫 화면: 서버와 연결이 잘 되는지만 확인합니다.
// 실제 화면(S1 메인 등)은 Figma 디자인을 보고 하나씩 만들어 갈 거예요.
function App() {
  const [server, setServer] = useState<ServerState>('checking')

  useEffect(() => {
    fetchHealth()
      .then((data) => setServer(data.status === 'ok' ? 'ok' : 'error'))
      .catch(() => setServer('error'))
  }, [])

  return (
    <main className="mx-auto max-w-[480px] px-4 py-6">
      <section className="rounded-3xl bg-linear-160 from-sunset-1 via-sunset-2 to-sunset-3 px-5 py-6 text-sunset-ink">
        <p className="text-sm">노을별</p>
        <h1 className="mt-2 font-display text-[28px] leading-tight">오늘 하늘 볼 만해?</h1>
        <p className="mt-2 text-[15px]">노을과 별이 잘 보일지 점수로 알려드려요</p>
      </section>
      <p className={`mt-4 text-sm ${STATUS_COLOR[server]}`}>{MESSAGE[server]}</p>
    </main>
  )
}

export default App
