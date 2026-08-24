import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import Input from '../../components/Input'
import Button from '../../components/Button'
import DoctorCard from '../../components/DoctorCard'
import LoadingSpinner, { Skeleton } from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import { searchDoctors } from '../../api/doctors'

export default function DoctorsPage() {
  const navigate = useNavigate()
  const [specialisation, setSpecialisation] = useState('')
  const [query, setQuery] = useState('') // committed filter actually sent to API
  const [doctors, setDoctors] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  async function load(filter) {
    setLoading(true)
    setError(null)
    try {
      setDoctors(await searchDoctors(filter || undefined))
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(query)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function onSubmit(e) {
    e.preventDefault()
    setQuery(specialisation.trim())
    load(specialisation.trim())
  }

  return (
    <>
      <PageHeader
        title="Find a doctor"
        subtitle="Browse active specialists and check their availability."
      />

      <form onSubmit={onSubmit} className="mb-5 flex flex-col gap-3 sm:flex-row">
        <div className="flex-1">
          <Input
            label="Filter by specialisation"
            placeholder="e.g. Cardiology"
            value={specialisation}
            onChange={(e) => setSpecialisation(e.target.value)}
          />
        </div>
        <div className="flex items-end gap-2">
          <Button type="submit" loading={loading}>
            Search
          </Button>
          {(query || specialisation) && (
            <Button
              variant="secondary"
              onClick={() => {
                setSpecialisation('')
                setQuery('')
                load('')
              }}
            >
              Clear
            </Button>
          )}
        </div>
      </form>

      {loading && !doctors && (
        <div className="grid gap-4 md:grid-cols-2">
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-32" />
          ))}
        </div>
      )}
      {loading && doctors && <LoadingSpinner />}

      {!loading && error && <ErrorState error={error} onRetry={() => load(query)} />}

      {!loading && !error && doctors?.length === 0 && (
        <EmptyState
          icon="🩺"
          title="No active doctors found"
          description={
            query
              ? `No active specialists match "${query}". Try a different specialisation.`
              : 'There are currently no active doctors listed. Please check back later.'
          }
          action={
            query ? (
              <Button variant="secondary" size="sm" onClick={() => { setSpecialisation(''); setQuery(''); load('') }}>
                Show all doctors
              </Button>
            ) : null
          }
        />
      )}

      {!loading && !error && doctors?.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2">
          {doctors.map((d) => (
            <DoctorCard
              key={d.id}
              doctor={d}
              onClick={() => navigate(`/patient/doctors/${d.id}`)}
            />
          ))}
        </div>
      )}
    </>
  )
}
