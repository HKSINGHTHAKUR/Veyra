import { useCallback, useEffect, useState } from 'react'
import Button from '../Button'
import { Skeleton } from '../LoadingSpinner'
import { getPreVisitSummary, generatePreVisit } from '../../api/ai'
import { toApiError } from '../../api/client'
import { aiActionMessage } from './helpers'
import {
  AiCard,
  AiError,
  AiNote,
  AiStatusMessage,
  UrgencyDisplay,
} from './shared'

const DISCLAIMER =
  'This assistant only organizes the information you provided — it does not diagnose, prescribe, or replace your doctor.'

/**
 * Pre-visit AI section. Fetches the existing summary (404 = not generated
 * yet). Generation is explicit user action only — never automatic.
 * `canGenerate` mirrors backend policy (owning patient / admin); the backend
 * remains the authority either way.
 */
export default function PreVisitSummaryCard({ appointmentId, canGenerate }) {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [generating, setGenerating] = useState(false)
  const [generateError, setGenerateError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      setSummary(await getPreVisitSummary(appointmentId))
    } catch (e) {
      setSummary(null)
      // 404 simply means no summary exists yet — not an error surface
      if (toApiError(e).status !== 404) setLoadError(e)
    } finally {
      setLoading(false)
    }
  }, [appointmentId])

  useEffect(() => {
    load()
  }, [load])

  async function onGenerate() {
    if (generating) return
    setGenerating(true)
    setGenerateError(null)
    try {
      setSummary(await generatePreVisit(appointmentId))
    } catch (e) {
      setGenerateError(aiActionMessage(e))
    } finally {
      setGenerating(false)
    }
  }

  const questionsOk =
    summary?.status === 'COMPLETED' &&
    Array.isArray(summary.suggestedQuestions) &&
    summary.suggestedQuestions.length === 3 &&
    summary.suggestedQuestions.every((q) => typeof q === 'string' && q.trim())

  return (
    <AiCard
      title="Pre-visit preparation"
      subtitle="AI-assisted summary of your symptoms"
    >
      {loading && (
        <div className="space-y-2" aria-hidden="true">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      )}

      {!loading && loadError && (
        <div className="space-y-3">
          <AiError>{toApiError(loadError).message}</AiError>
          <Button variant="secondary" size="sm" onClick={load}>
            Try again
          </Button>
        </div>
      )}

      {!loading && !loadError && !summary && (
        <div className="space-y-3">
          <AiStatusMessage icon="📝">
            The assistant can organize your symptoms into a concise summary and suggest questions
            to discuss with your doctor.
          </AiStatusMessage>
          {canGenerate ? (
            <Button onClick={onGenerate} loading={generating}>
              {generating ? 'Generating summary…' : 'Generate pre-visit summary'}
            </Button>
          ) : (
            <p className="text-xs text-slate-400">
              The patient can generate this summary from their appointment page.
            </p>
          )}
          {generateError && <AiError>{generateError}</AiError>}
        </div>
      )}

      {!loading && summary?.status === 'PENDING' && (
        <div className="space-y-3">
          <AiStatusMessage icon="⏳">Summary is being prepared.</AiStatusMessage>
          <Button variant="secondary" size="sm" onClick={load}>
            Refresh
          </Button>
        </div>
      )}

      {!loading && summary?.status === 'FAILED' && (
        <div className="space-y-3">
          <AiStatusMessage icon="⚠️">Unable to generate the summary right now.</AiStatusMessage>
          {canGenerate ? (
            <Button variant="secondary" onClick={onGenerate} loading={generating}>
              {generating ? 'Generating summary…' : 'Try again'}
            </Button>
          ) : null}
          {generateError && <AiError>{generateError}</AiError>}
        </div>
      )}

      {!loading && summary?.status === 'COMPLETED' && !questionsOk && (
        <AiError>The summary arrived in an unexpected format. Please try regenerating it.</AiError>
      )}

      {!loading && summary?.status === 'COMPLETED' && questionsOk && (
        <div>
          {summary.urgency && (
            <div className="mb-3">
              <UrgencyDisplay urgency={summary.urgency} />
            </div>
          )}

          <h3 className="text-xs font-medium uppercase tracking-wide text-slate-400">
            Chief complaint
          </h3>
          <p className="mt-1 text-sm leading-relaxed text-slate-700">{summary.chiefComplaint}</p>

          <h3 className="mt-4 text-xs font-medium uppercase tracking-wide text-slate-400">
            Questions to discuss with your doctor
          </h3>
          <ol className="mt-2 space-y-2">
            {summary.suggestedQuestions.map((q, i) => (
              <li
                key={i}
                className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-2.5"
              >
                <span
                  aria-hidden="true"
                  className="mt-0.5 text-xs font-semibold tabular-nums text-primary-600"
                >
                  {String(i + 1).padStart(2, '0')}
                </span>
                <span className="text-sm text-slate-700">{q}</span>
              </li>
            ))}
          </ol>

          <AiNote>{DISCLAIMER}</AiNote>
        </div>
      )}
    </AiCard>
  )
}
