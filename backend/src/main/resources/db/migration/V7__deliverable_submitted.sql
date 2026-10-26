-- The student's own submission state (include[]=submission): submitted work is not planned.
ALTER TABLE deliverable ADD COLUMN submitted boolean NOT NULL DEFAULT false;
