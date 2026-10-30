-- Generic job queue. Knows nothing about photos: tenant_id is an opaque fairness key,
-- so it carries no foreign key and benchmarks can use synthetic tenants.

create table jobqueue.jobs (
    id            bigint      generated always as identity primary key,
    queue         text        not null,                  -- unit of concurrency limits
    type          text        not null,                  -- handler lookup key
    tenant_id     text        not null,                  -- fairness key (photographer id)
    payload       jsonb       not null default '{}',
    status        text        not null,
    run_at        timestamptz not null default now(),    -- not claimable before this; retries back off by pushing it out
    attempts      int         not null default 0,        -- incremented at claim, so crash loops count
    max_attempts  int         not null,
    pending_deps  int         not null default 0,        -- unfinished dependencies
    locked_by     text,                                  -- worker id holding the lease
    locked_until  timestamptz,                           -- visibility timeout; heartbeat extends it
    last_error    text,
    dedupe_key    text        unique,                    -- idempotent enqueue; null = no dedupe
    created_at    timestamptz not null default now(),
    started_at    timestamptz,
    finished_at   timestamptz,

    constraint jobs_status_valid
        check (status in ('BLOCKED', 'READY', 'RUNNING', 'SUCCEEDED', 'DEAD')),
    constraint jobs_counters_valid
        check (attempts >= 0 and max_attempts >= 1 and attempts <= max_attempts and pending_deps >= 0),
    -- BLOCKED exactly while dependencies are outstanding.
    constraint jobs_blocked_iff_pending_deps
        check ((status = 'BLOCKED') = (pending_deps > 0)),
    -- A running job always holds a lease the reaper can expire.
    constraint jobs_running_has_lease
        check (status <> 'RUNNING' or (locked_by is not null and locked_until is not null))
);

-- Claim paths. Partial on READY so finished history never slows a claim.
-- fair: per-tenant oldest job; fifo: global oldest job (baseline for the starvation benchmark).
create index jobs_claim_fair on jobqueue.jobs (queue, tenant_id, run_at, id) where status = 'READY';
create index jobs_claim_fifo on jobqueue.jobs (queue, run_at, id) where status = 'READY';

-- Reaper: find leases held by crashed workers.
create index jobs_leases on jobqueue.jobs (locked_until) where status = 'RUNNING';

create table jobqueue.job_dependencies (
    depends_on_id bigint not null references jobqueue.jobs on delete cascade,
    job_id        bigint not null references jobqueue.jobs on delete cascade,
    primary key (depends_on_id, job_id),                 -- completion looks up "who waits on me"
    constraint job_dependencies_not_self check (depends_on_id <> job_id)
);

-- Deleting a job (purge) must find edges pointing at it without a full scan.
create index job_dependencies_job on jobqueue.job_dependencies (job_id);
