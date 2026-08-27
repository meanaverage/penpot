import { z } from 'zod/v3';
import { manifestSchema } from './manifest.schema.js';

export type Manifest = z.infer<typeof manifestSchema>;
export type Permissions = Manifest['permissions'][number];
