import { defineCollection } from "astro:content";
import { docsLoader } from "@astrojs/starlight/loaders";
import { docsSchema } from "@astrojs/starlight/schema";
import { z } from "astro/zod";

const timestamp = z.string().regex(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/);
const kebabCase = z.string().regex(/^[\p{L}\p{N}]+(?:-[\p{L}\p{N}]+)*$/u);

export const collections = {
  docs: defineCollection({
    loader: docsLoader(),
    schema: docsSchema({
      extend: z.object({
        created: timestamp,
        modified: timestamp,
        type: z.enum([
          "concept",
          "article",
          "course",
          "collection",
          "map",
          "documentation",
        ]),
        status: z.enum(["inbox", "active", "paused", "maintained"]),
        maturity: z.enum(["seed", "developing", "stable"]),
        aliases: z.array(z.string()).optional(),
        tags: z.array(kebabCase).min(1),
        source: z.url().optional(),
      }),
    }),
  }),
};
