---
name: design-md
description: Library of 70+ ready-made DESIGN.md design systems modeled on real products (Stripe, Linear, Vercel, Apple, Notion, Airbnb, Spotify, Figma, Supabase, and more). Use when the user wants a UI to look or feel like a named product or brand, asks for a DESIGN.md, or wants a proven design system to start a frontend from.
---

# DESIGN.md library

A DESIGN.md is a plain-markdown design system that covers colors, typography, spacing, components, and do's and don'ts. Coding agents read it to keep UI consistent. This skill bundles the [awesome-design-md](https://github.com/VoltAgent/awesome-design-md) collection (MIT), with one file per product at `designs/<name>/DESIGN.md`, relative to this skill's directory.

## Available designs

<!-- catalog:start (VoltAgent/awesome-design-md@f696123, 74 designs) -->
`airbnb`, `airtable`, `apple`, `binance`, `bmw`, `bmw-m`, `bugatti`, `cal`, `claude`, `clay`, `clickhouse`, `cohere`, `coinbase`, `composio`, `cursor`, `dell-1996`, `elevenlabs`, `expo`, `ferrari`, `figma`, `framer`, `hashicorp`, `hp`, `ibm`, `intercom`, `kraken`, `lamborghini`, `linear.app`, `lovable`, `mastercard`, `meta`, `minimax`, `mintlify`, `miro`, `mistral.ai`, `mongodb`, `nike`, `nintendo-2001`, `notion`, `nvidia`, `ollama`, `opencode.ai`, `pinterest`, `playstation`, `posthog`, `raycast`, `renault`, `replicate`, `resend`, `revolut`, `runwayml`, `sanity`, `sentry`, `shopify`, `slack`, `spacex`, `spotify`, `starbucks`, `stripe`, `supabase`, `superhuman`, `tesla`, `theverge`, `together.ai`, `uber`, `vercel`, `vodafone`, `voltagent`, `warp`, `webflow`, `wired`, `wise`, `x.ai`, `zapier`
<!-- catalog:end -->

## How to use

1. **Pick the design.** Match the user's request to a name in the catalog. For example, "like Linear" maps to `linear.app` and "Mistral" maps to `mistral.ai`. If several fit or the request is vague ("something like a fintech app"), suggest two or three options with a one-line reason each, based on the files' opening sections, and let the user choose. If nothing matches, say so and offer the closest alternatives. The upstream project also takes requests at https://getdesign.md/request.
2. **Install it into the project.** Copy `designs/<name>/DESIGN.md` from this skill's directory to `DESIGN.md` in the project root. If a `DESIGN.md` already exists, show the user what it is and ask before replacing it. Offer to save the new one as `DESIGN.<name>.md` instead.
3. **Build from it.** Read the installed DESIGN.md fully before writing UI code, and follow its tokens, type scale, spacing, and component rules. When the user later asks for changes that conflict with it, point out the conflict and update DESIGN.md if they want the change kept.

To blend designs, such as Stripe's typography with Linear's color, read both files. Then write a merged DESIGN.md that says where each part came from.

These files describe a visual *style* inspired by each product. Don't copy logos, trademarks, or proprietary assets into the user's project.
