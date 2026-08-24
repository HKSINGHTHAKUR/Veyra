import { useCallback, useEffect, useState } from 'react'
import Button from '../Button'
import { Skeleton } from '../LoadingSpinner'
import { getPostVisitSummary, generatePostVisit } from '../../api/ai'
import { toApiError } from '../../api/client'
import { aiActionMessage } from './helpers'
import { AiCard, AiError, AiNote, AiStatusMessage } from './shared'

/**
 * Post-visit AI section. Only rendered by callers once a consultation exists.
 * Manual generation is doctor/admin-only per backend policy; patients get a
 * read-only view. Medications are rendered exactly as returned — they are
 * validated against the prescription records server-side.
 */
export default function PostVisitSummaryCard({ appointmentId, canGenerate }) {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [generating, setGenerating] = useState(false)
  const [generateError, setGenerateError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      setSummary(await getPostVisitSummary(appointmentId))
    } catch (e) {
      setSummary(null)
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
      setSummary(await generatePostVisit(appointmentId))
    } catch (e) {
      setGenerateError(aiActionMessage(e))
    } finally {
      setGenerating(false)
    }
  }

  const completedOk = summary?.status === 'COMPLETED' && summary.summaryText

  return (
    <AiCard title="Post-visit summary" subtitle="AI-assisted recap of the consultation">
      {loading && (
        <div className="space-y-2" aria-hidden="true">
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
          <AiStatusMessage icon="📋">
            {canGenerate
              ? 'Generate a patient-friendly recap of this consultation, including medications and follow-up steps.'
              : 'Your doctor can prepare a patient-friendly recap of this consultation, including medications and follow-up steps.'}
          </AiStatusMessage>
          {canGenerate && (
            <Button onClick={onGenerate} loading={generating}>
              {generating ? 'Generating summary…' : 'Generate post-visit summary'}
            </Button>
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
          {canGenerate && (
            <Button variant="secondary" onClick={onGenerate} loading={generating}>
              {generating ? 'Generating summary…' : 'Try again'}
            </Button>
          )}
          {generateError && <AiError>{generateError}</AiError>}
        </div>
      )}

      {!loading && summary?.status === 'COMPLETED' && !completedOk && (
        <AiError>The summary arrived in an unexpected format. Please try regenerating it.</AiError>
      )}

      {!loading && completedOk && (
        <div>
          <h3 className="text-xs font-medium uppercase tracking-wide text-slate-400">
            Visit recap
          </h3>
          <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
            {summary.summaryText}
          </p>

          <h3 className="mt-4 text-xs font-medium uppercase tracking-wide text-slate-400">
            Medications
          </h3>
          {Array.isArray(summary.medications) && summary.medications.length > 0 ? (
            <ul className="mt-2 divide-y divide-slate-100 rounded-lg border border-slate-200">
              {summary.medications.map((m, i) => (
                <li key={i} className="px-3 py-2.5 text-sm">
                  <span className="font-medium text-slate-900">{m.name}</span>
                  <span className="text-slate-500">
                    {m.dosage ? ` — ${m.dosage}` : ''}
                    {m.frequency ? `, ${m.frequency}` : ''}
                    {m.duration ? `, ${m.duration}` : ''}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-1 text-sm text-slate-500">No medications were prescribed.</p>
          )}

          <h3 className="mt-4 text-xs font-medium uppercase tracking-wide text-slate-400">
            Follow-up steps
          </h3>
          {Array.isArray(summary.followUpSteps) && summary.followUpSteps.length > 0 ? (
            <ol className="mt-2 space-y-2">
              {summary.followUpSteps.map((step, i) => (
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
                  <span className="text-sm text-slate-700">{step}</span>
                </li>
              ))}
            </ol>
          ) : null}

          <AiNote>
            Medications come directly from the doctor&apos;s prescription record. This recap is
            informational and does not replace your doctor&apos;s advice.
          </AiNote>
        </div>
      )}
    </AiCard>
  )
}
