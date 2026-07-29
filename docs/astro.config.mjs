import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";

export default defineConfig({
  site: "https://azusachino.github.io/flos",
  integrations: [
    starlight({
      title: "Flos",
      description: "Executable tutorials for distributed systems concepts",
      social: [
        {
          icon: "github",
          label: "GitHub",
          href: "https://github.com/azusachino/flos",
        },
      ],
      sidebar: [
        {
          label: "Start here",
          items: [
            { label: "Welcome", slug: "" },
            { label: "Prerequisites", slug: "getting-started/prerequisites" },
            {
              label: "Run the examples",
              slug: "getting-started/running-examples",
            },
          ],
        },
        {
          label: "Apache Flink",
          items: [{ autogenerate: { directory: "concepts/flink" } }],
        },
        {
          label: "Contributing",
          items: [{ autogenerate: { directory: "contributing" } }],
        },
      ],
    }),
  ],
});
