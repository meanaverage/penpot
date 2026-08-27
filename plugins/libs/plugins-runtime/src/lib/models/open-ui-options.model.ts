import { z } from 'zod/v3';
import { openUISchema } from './open-ui-options.schema.js';

export type OpenUIOptions = z.infer<typeof openUISchema>;
