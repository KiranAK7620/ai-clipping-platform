# ClipTova — Product & Development Master Plan

**Product:** ClipTova  
**Category:** AI Video Repurposing SaaS  
**Tagline:** Every long video has a short story.  
**Development approach:** Solo developer + agentic coding with Codex  
**Initial release:** Private personal beta  
**Initial market:** India  
**Budget ceiling:** ₹5,000  
**Document purpose:** Primary product requirements and implementation instructions for Codex.

---

# 1. Product Vision

ClipTova is an AI-powered video repurposing platform that converts long-form videos into engaging short-form videos.

Users upload long videos. ClipTova analyses their content, discovers interesting moments, recommends highlights and automatically generates short vertical clips.

The platform should support:

- Podcasts and interviews
- Gaming streams
- Educational videos
- Discussions and presentations
- Other authorized video content

The long-term vision is to become a complete AI content repurposing studio.

Do not build just a basic video cutter. Build a modular platform that can evolve into a full AI video production product.

## Core product promise

Upload one long video. Discover its best moments. Generate multiple ready-to-share clips.

# 2. Target Users

Initial target audience: Indian content creators.

Primary segments:

1. Podcast creators
2. Gaming streamers
3. Educators and online teachers
4. Interview and discussion channels

Language priorities:

- English
- Hindi
- Hinglish
- Marathi
- Other Indian languages as the platform evolves

# 3. Core Product Features

## 3.1 Video Upload

Users should be able to:

- Upload local video files.
- View upload progress.
- See video duration, resolution and file size.
- View processing status.
- Cancel a processing job where safe.
- Retry failed jobs.

Initial version supports direct local file uploads only.

Do not implement unrestricted YouTube or third-party platform downloading. Future URL integrations must use authorized APIs or permitted import mechanisms.

## 3.2 AI Video Analysis

The platform should:

1. Extract audio using FFmpeg.
2. Generate a timestamped transcript.
3. Analyse transcript content.
4. Identify interesting moments.
5. Generate highlight candidates.
6. Score and rank candidates.
7. Recommend clips with meaningful start and end timestamps.

AI must identify complete thoughts and conversations, not simply cut random sentences.

## 3.3 Two Clipping Modes

### Automatic Mode

The AI selects the strongest moments and automatically generates clips.

User can specify:

- Number of clips.
- Desired duration.
- Content category.
- Language.
- Caption preferences.

### Assisted Mode

The AI recommends possible highlights.

User can:

- Review recommendations.
- Preview the selected moments.
- Adjust start and end timestamps.
- Select or reject highlights.
- Generate only the selected clips.

Both modes are mandatory MVP features.

## 3.4 Clip Duration

Support:

- 30 seconds
- 45 seconds
- 60 seconds
- Custom duration later

## 3.5 Video Output

Initial output requirements:

- Vertical 9:16 format.
- MP4 format.
- H.264 video encoding.
- AAC audio.
- Burned-in subtitles.
- Configurable subtitle appearance.
- Preview and download.

Original video must never be modified.

# 4. Application Design and Branding

## Brand Identity

Brand name: ClipTova

Tagline: Every long video has a short story.

Brand personality:

- Intelligent
- Modern
- Creative
- Reliable
- Premium

## Visual Design System

Use a dark creative studio theme.

Primary colour: #477BFF  
Secondary colour: #8957FF  
Background: #101426  
Card background: #202A45  
Primary text: #F8FAFC  
Secondary text: #AAB5D0

Typography: Inter.

Design principles:

- Clean SaaS dashboard.
- Consistent spacing.
- Rounded cards.
- Subtle borders.
- Clear visual hierarchy.
- Minimal unnecessary animations.
- Responsive layouts.
- Accessible colour contrast.

Avoid excessive gradients and excessive use of glowing effects.

## Main Website

Create these pages:

1. Landing page
2. Features
3. How it works
4. Pricing placeholder
5. About ClipTova

Public website can be developed initially as a local preview. Do not implement payment processing or public registration in the personal beta.

## Application Dashboard

Create the following sections:

1. Dashboard
2. New Project
3. My Projects
4. Video Processing
5. AI Highlights
6. Clip Editor
7. Generated Clips
8. Settings
9. Admin AI Configuration

## Dashboard Experience

Display:

- Total uploaded videos.
- Completed projects.
- Generated clips.
- Recent projects.
- Processing jobs.
- Quick upload action.

## Clip Editor

The editor should provide:

- Video preview.
- Start and end timestamps.
- Clip duration.
- Subtitle preview.
- Aspect ratio selection.
- Regenerate clip.
- Download clip.

Do not build a complicated professional timeline editor in the MVP.

# 5. Technology Stack

Use the following technology stack.

## Frontend

- React
- TypeScript
- Vite
- Material UI
- React Query
- Zustand where appropriate

Keep UI components reusable.

## Backend

- Java 17
- Spring Boot 3.x
- Maven
- Spring Web
- Spring Data JPA
- Hibernate
- Bean Validation

Use a modular monolithic architecture.

Do not introduce microservices.

## Database

- PostgreSQL
- Docker
- Flyway database migrations

Existing PostgreSQL Docker container:

Container name: postgres-dev  
PostgreSQL version: 18.6  
Host port: 5434  
Existing database: devdb

Create a separate application database:

Database: clipping_db  
Application user: clipping_user

Do not modify or delete devdb.

Use environment variables for passwords.

## Video Processing

Use FFmpeg.

Responsibilities:

- Metadata extraction.
- Audio extraction.
- Video trimming.
- Vertical video conversion.
- Subtitle rendering.
- Final MP4 generation.

Invoke FFmpeg from Java using ProcessBuilder.

Do not execute shell commands using unsanitized user input.

## Transcription

Start with local Whisper through whisper.cpp.

Requirements:

- Timestamped transcription.
- Multilingual support.
- English and Hindi testing.
- Hinglish testing.
- Marathi support evaluation.

Transcription should be independent of the LLM provider architecture.

## Storage

Initial implementation:

- Store uploaded files locally.
- Store generated clips locally.
- Use configurable storage paths.
- Keep original videos separate from generated output.

Prefer the external SSD for large video files.

Do not store large binary videos directly in PostgreSQL.

# 6. AI Provider Architecture

A major product differentiator is the ability for the owner to configure multiple AI providers.

Initial providers to investigate:

- DeepSeek
- Alibaba Qwen
- Moonshot Kimi
- Zhipu GLM
- MiniMax
- OpenAI
- Anthropic Claude
- Google Gemini

Do not assume all providers have identical APIs or capabilities.

Create a provider abstraction layer.

Suggested components:

- LlmProvider interface
- ProviderRegistry
- ProviderConfigurationService
- ModelRoutingService
- AIUsageTrackingService
- AIProviderFactory

Create individual provider adapters.

The owner should be able to:

- Enable or disable providers.
- Configure API keys.
- Select default models.
- Configure fallback providers.
- Set task-specific provider preferences.
- Set spending limits.
- View API usage and estimated costs.
- Review provider errors.

API keys must never be exposed to frontend users.

Keep provider configuration and secrets on the backend.

Do not implement all eight providers in the first milestone. Build the abstraction first, integrate one provider, test it, and add other providers incrementally.

Use separate capability interfaces where appropriate for text, vision and audio tasks.

# 7. AI Video Processing Pipeline

Implement the following pipeline:

STEP 1: VIDEO UPLOAD

Accept an authorized local video file.

STEP 2: VIDEO METADATA

Use FFprobe to extract:

- Duration
- Resolution
- Frame rate
- Video codec
- Audio codec
- File size

STEP 3: AUDIO EXTRACTION

Extract audio using FFmpeg.

STEP 4: TRANSCRIPTION

Use Whisper to produce timestamped transcript segments.

STEP 5: CONTENT ANALYSIS

Send transcript segments to the configured LLM.

Analyse:

- Interesting conversations
- Strong statements
- Questions and answers
- Funny moments
- Emotional moments
- Educational explanations
- Important conclusions

STEP 6: HIGHLIGHT GENERATION

Return structured JSON containing:

- Start timestamp
- End timestamp
- Suggested title
- Hook
- Description
- Category
- Reason for selection
- Quality score

STEP 7: VALIDATION

Validate timestamps against actual video duration and transcript segments.

Reject invalid or hallucinated timestamps.

Preserve enough context for a complete thought.

STEP 8: RANKING

Rank candidate clips by:

- Interesting content
- Hook quality
- Completeness
- Engagement potential
- Duration suitability

Initial scoring weights are experimental and must be adjustable.

STEP 9: USER SELECTION

Support automatic and assisted modes.

STEP 10: VIDEO GENERATION

Use FFmpeg to create vertical clips with subtitles.

STEP 11: PREVIEW AND DOWNLOAD

Save generated clips and expose them through the application.

All long-running processing must happen asynchronously.

Do not hold an HTTP request open during video processing.

Persist job status so the application can recover from failures or restarts.

# 8. Content-Specific AI Intelligence

Use different analysis strategies for different categories.

## Podcast and Interviews

Prioritize:

- Strong opinions.
- Surprising statements.
- Interesting questions.
- Emotional reactions.
- Complete conversations.
- Strong opening hooks.

## Gaming

Prioritize:

- Exciting moments.
- Unexpected events.
- Reactions.
- Wins and losses.
- Funny situations.

Important: Transcript analysis alone is insufficient for gaming. Plan future support for sampled video frames and audio-event analysis.

## Education

Prioritize:

- Complete explanations.
- Useful learning points.
- Definitions.
- Examples.
- Important takeaways.

Avoid clips that begin or end in the middle of an explanation.

# 9. Database Design

Start with the following entities:

- User
- Project
- Video
- ProcessingJob
- Transcript
- TranscriptSegment
- HighlightCandidate
- GeneratedClip
- AIProvider
- AIModel
- AIUsageRecord
- ApplicationSetting

Use UUID primary keys.

Use appropriate indexes and foreign keys.

Use Flyway migrations.

Store video paths and metadata, not binary video files.

Authentication may initially be owner-only or disabled for the local personal beta, but keep the architecture ready for authentication later.

# 10. Backend Project Structure

Suggested structure:

backend/
  src/main/java/
    config/
    common/
    project/
    video/
    storage/
    processing/
    transcription/
    highlight/
    clipping/
    ai/
      provider/
      routing/
      usage/
    admin/

Keep domain logic separate from controllers.

Use DTOs rather than exposing JPA entities directly.

Add validation and structured exception handling.

# 11. Frontend Project Structure

Suggested structure:

frontend/
  src/
    app/
    components/
    features/
      dashboard/
      projects/
      upload/
      processing/
      highlights/
      editor/
      clips/
      settings/
      admin/
    hooks/
    services/
    stores/
    types/
    theme/

Build reusable components and maintain consistent design tokens.

# 12. Security and Reliability

Mandatory requirements:

- Validate uploaded file types.
- Limit file sizes through configuration.
- Prevent path traversal.
- Keep secrets outside Git.
- Validate AI-generated JSON.
- Sanitize filenames.
- Restrict local file access to configured storage directories.
- Handle processing failures.
- Implement job status tracking.
- Log errors without exposing API keys.
- Do not expose admin AI configuration to normal users.

Only process content the user owns or has permission to use.

Do not build copyright-protection bypasses or unauthorized downloading mechanisms.

# 13. Development Roadmap

Build incrementally.

## Phase 0 — Project Foundation

- Create Git repository.
- Create frontend project.
- Create Spring Boot backend.
- Configure PostgreSQL connection.
- Configure Flyway.
- Establish project structure.
- Add environment configuration.
- Create initial README.

Acceptance criteria:

Frontend and backend start successfully. Database connection works. Flyway migration runs successfully.

## Phase 1 — Video Upload

- Build dashboard.
- Build upload interface.
- Implement backend upload API.
- Save original files.
- Extract metadata with FFprobe.
- Display uploaded videos.

Acceptance criteria:

Upload a real video, save it locally and display its metadata.

## Phase 2 — Transcription

- Integrate whisper.cpp.
- Extract audio.
- Generate timestamped transcript.
- Store transcript and segments.
- Display transcript in frontend.

Acceptance criteria:

Successfully transcribe an authorized English or Hindi test video and show timestamps.

## Phase 3 — AI Highlights

- Implement LLM abstraction.
- Integrate first provider.
- Generate highlight candidates.
- Validate timestamps.
- Build ranking logic.
- Implement assisted selection.

Acceptance criteria:

AI recommends meaningful clips with valid timestamps and the user can select them.

## Phase 4 — Clip Generation

- Implement FFmpeg trimming.
- Generate 9:16 output.
- Add subtitles.
- Implement 30/45/60-second presets.
- Add preview and download.

Acceptance criteria:

Successfully generate and download a vertical video with readable subtitles.

## Phase 5 — Automatic Clipping

- Implement automatic mode.
- Rank candidates.
- Select top clips.
- Generate multiple clips.
- Display processing progress.

Acceptance criteria:

User uploads one video and receives multiple generated clips without manually selecting timestamps.

## Phase 6 — AI Admin Panel

- Provider configuration.
- API key management.
- Model selection.
- Task-specific routing.
- Fallback configuration.
- Usage and cost monitoring.

Acceptance criteria:

Owner can switch providers without changing core video-processing logic.

## Phase 7 — Quality and Private Beta

Test:

- Podcasts.
- Gaming videos.
- Educational content.
- English.
- Hindi.
- Hinglish.
- Marathi where supported.

Evaluate:

- Timestamp accuracy.
- Complete context.
- Clip quality.
- Subtitle accuracy.
- Output quality.
- Processing time.
- AI cost per video.

Only after these tests should we consider public registration, subscriptions and cloud hosting.

# 14. Budget and Infrastructure Constraints

Maximum initial budget: ₹5,000.

Target approach:

- Local development.
- Local video processing.
- Local transcription.
- Free/open-source tools wherever practical.
- Limited API testing budget.
- No paid GPU hosting.
- No unnecessary SaaS subscriptions.
- No public cloud infrastructure initially.

Track AI usage and API cost from the beginning.

The application should support local execution on an Apple Silicon Mac with 16GB RAM.

Avoid memory-intensive processing where possible.

Process videos sequentially initially.

# 15. Non-Goals for MVP

Do not build these features initially:

- Public multi-user registration.
- Payment gateway.
- Subscription billing.
- Social media publishing.
- Public API.
- Marketplace.
- Complex professional timeline editor.
- Microservices.
- Kubernetes.
- Cloud GPU infrastructure.
- Unrestricted third-party video downloading.

These can be considered after the private beta succeeds.

# 16. Testing Strategy

Add automated tests for:

- API endpoints.
- Database repositories.
- Timestamp validation.
- Highlight JSON parsing.
- Provider adapters.
- Job state transitions.
- FFmpeg command construction.
- Storage validation.

Create a small evaluation dataset of videos the owner has permission to use.

Track quality metrics and manually review generated clips.

Do not claim AI-generated highlights are viral. Measure their quality using repeatable tests and human review.

# 17. Instructions for Codex

You are the primary coding agent for ClipTova.

Follow these rules:

1. Read this entire document before coding.
2. Treat this document as the primary product requirements source.
3. Do not attempt to build the entire application in one step.
4. Work phase by phase.
5. Before each phase, explain the implementation plan.
6. Create a checklist of tasks.
7. Implement only the current milestone.
8. Run tests after implementation.
9. Fix errors before moving forward.
10. Explain every important architectural decision.
11. Update README and technical documentation.
12. Never overwrite existing user files without permission.
13. Never delete or recreate existing Docker databases.
14. Never expose API keys or passwords.
15. Never commit secrets to Git.
16. Do not silently change the technology stack.
17. Prefer simple maintainable solutions over unnecessary complexity.
18. Ask before introducing additional paid services.
19. Ask before destructive migrations or commands.
20. Maintain a clear development progress log.

For every completed phase, report:

- Features implemented.
- Files created or modified.
- Commands required to run the application.
- Tests executed and their results.
- Known issues.
- Remaining tasks.
- Estimated next milestone.

Do not claim a feature is complete until it has been tested.

# 18. First Task for Codex

Begin with Phase 0 only.

First inspect the development environment and existing project directory.

Confirm:

- Java 17.
- Maven 3.9.11.
- Node.js 22.
- npm.
- Git.
- FFmpeg.
- Existing PostgreSQL Docker container.

Use the existing PostgreSQL container at localhost:5434.

Do not modify the existing devdb database.

If clipping_db and clipping_user do not exist, ask for approval before creating them.

Then:

1. Propose the repository structure.
2. Initialize Git.
3. Create the React + TypeScript frontend.
4. Create the Spring Boot backend using Java 17.
5. Configure PostgreSQL connection through environment variables.
6. Configure Flyway.
7. Create a health-check endpoint.
8. Create a basic frontend shell using the ClipTova dark theme.
9. Write setup instructions in README.
10. Run the application and verify database connectivity.

Stop after Phase 0.

Do not start video upload or AI integration until Phase 0 is reviewed and approved.

---

**Final product principle:** ClipTova should be a modular, affordable, reliable AI video repurposing platform. Build a working product before adding complexity.