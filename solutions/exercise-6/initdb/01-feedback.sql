-- Conference feedback seed data for Exercise 6.
-- Runs once when the PostgreSQL container is first created (docker-entrypoint-initdb.d).
-- The table schema matches what Hibernate generates from the Feedback @Entity;
-- hbm2ddl.auto=update will find it already in place and leave it unchanged.

CREATE TABLE IF NOT EXISTS session_feedback (
    id            BIGSERIAL    PRIMARY KEY,
    speaker       VARCHAR(255) NOT NULL,
    session_title VARCHAR(512),
    rating        INTEGER      NOT NULL,
    comment       VARCHAR(1024),
    submitted_at  TIMESTAMP    NOT NULL
);

INSERT INTO session_feedback (speaker, session_title, rating, comment, submitted_at) VALUES

-- Mario Fusco — Building Production-Ready Agentic Systems with LangChain4j and Quarkus (Oct 6, 16:50, TBA 8)
('Mario Fusco', 'Building Production-Ready Agentic Systems with LangChain4j and Quarkus', 5,
 'Best hands-on demo I''ve seen all conference. The live coding was flawless.',                          NOW()),
('Mario Fusco', 'Building Production-Ready Agentic Systems with LangChain4j and Quarkus', 5,
 'Finally a talk that shows real production patterns, not just hello-world agents.',                     NOW()),
('Mario Fusco', 'Building Production-Ready Agentic Systems with LangChain4j and Quarkus', 4,
 'Excellent content. Could have gone deeper on error handling in agentic loops.',                        NOW()),
('Mario Fusco', 'Building Production-Ready Agentic Systems with LangChain4j and Quarkus', 5,
 'Immediately applicable to our team. Walked out with a clear migration plan.',                          NOW()),
('Mario Fusco', 'Building Production-Ready Agentic Systems with LangChain4j and Quarkus', 4,
 'Great pacing and very practical. The cost-of-inference demo was a nice touch.',                        NOW()),

-- Mario Fusco — A Year of Agentic AI Evolution: Lessons Learned Building Production-grade Agentic Systems (Oct 7, 14:00, TBA 7)
('Mario Fusco', 'A Year of Agentic AI Evolution: Lessons Learned Building Production-grade Agentic Systems', 5,
 'Honest account of what works and what doesn''t in production. Loved the war stories.',                 NOW()),
('Mario Fusco', 'A Year of Agentic AI Evolution: Lessons Learned Building Production-grade Agentic Systems', 4,
 'Very insightful. The section on observability gaps was eye-opening.',                                  NOW()),
('Mario Fusco', 'A Year of Agentic AI Evolution: Lessons Learned Building Production-grade Agentic Systems', 5,
 'One of the top three talks of Devoxx. The lessons-learned format was perfect.',                        NOW()),

-- Mario Fusco — The Quarkus advantage: Turning build-time metaprogramming into runtime performances (Oct 5, 13:30)
('Mario Fusco', 'The Quarkus advantage: Turning build-time metaprogramming into runtime performances',   5,
 'Incredibly clear explanation of what Quarkus does at build time. Changed my mental model.',            NOW()),
('Mario Fusco', 'The Quarkus advantage: Turning build-time metaprogramming into runtime performances',   4,
 'Great benchmarks. Would have loved a comparison with GraalVM native.',                                 NOW()),

-- Guillaume Laforge — Choose your own adventure in agentic design patterns
('Guillaume Laforge', 'Choose your own adventure in agentic design patterns', 5,
 'Interactive format was inspired. Each design pattern came to life through the audience choices.',       NOW()),
('Guillaume Laforge', 'Choose your own adventure in agentic design patterns', 5,
 'Hands-down my favourite format of the conference. Clever and educational.',                            NOW()),
('Guillaume Laforge', 'Choose your own adventure in agentic design patterns', 4,
 'Really enjoyed it. The parallelism pattern demo was surprisingly easy to follow.',                     NOW()),

-- Guillaume Laforge — Loop Engineering, beyond prompt, context, and harness engineering
('Guillaume Laforge', 'Loop Engineering, beyond prompt, context, and harness engineering',               5,
 'Exactly the depth I was looking for. Moved my thinking beyond simple prompt engineering.',             NOW()),
('Guillaume Laforge', 'Loop Engineering, beyond prompt, context, and harness engineering',               4,
 'Dense but worth it. Glad I took notes.',                                                               NOW()),

-- Nicolai Parlog — Modern Java in Practice - The Features That Matter
('Nicolai Parlog', 'Modern Java in Practice - The Features That Matter',                                 5,
 'The best Java update talk I''ve attended. Every feature was justified with a real use case.',          NOW()),
('Nicolai Parlog', 'Modern Java in Practice - The Features That Matter',                                 5,
 'Clear, opinionated, and practical. Pattern matching section alone was worth the trip.',                NOW()),
('Nicolai Parlog', 'Modern Java in Practice - The Features That Matter',                                 4,
 'Excellent. Wish there had been more time on structured concurrency.',                                  NOW()),

-- Venkat Subramaniam — ADRs: The Why and How
('Venkat Subramaniam', 'ADRs: The Why and How',                                                          5,
 'Venkat makes every topic feel obvious in hindsight. Left with a concrete ADR template.',               NOW()),
('Venkat Subramaniam', 'ADRs: The Why and How',                                                          5,
 'Will be introducing ADRs to my team next sprint. That''s the highest praise I can give.',             NOW()),

-- Christian Tzolov — Agent Loops, Decoded: Fundamentals to Agentic Patterns in Spring AI
('Christian Tzolov', 'Agent Loops, Decoded: Fundamentals to Agentic Patterns in Spring AI',              5,
 'Incredible depth and breadth in 3 hours. Spring AI suddenly feels approachable.',                      NOW()),
('Christian Tzolov', 'Agent Loops, Decoded: Fundamentals to Agentic Patterns in Spring AI',              4,
 'Great workshop. The subagent section could use a bit more time.',                                      NOW()),
('Christian Tzolov', 'Agent Loops, Decoded: Fundamentals to Agentic Patterns in Spring AI',              5,
 'The evaluation loop demo convinced me to rethink our entire agent architecture.',                      NOW());
