
import os.path

curr_dir = os.path.dirname(os.path.realpath(__file__))

# Get all sub-directories
all_dirs = [d for d in os.listdir(curr_dir) if os.path.isdir(os.path.join(d))]

dirs = [d for d in all_dirs if "section-" in d ]

class SubSection():
    def __init__(self, name, link):
        self.name = name
        self.link = link

    def __str__(self):
        return "[" + self.name + "](" + self.link + ")"

class Section():
    def __init__(self, name, subsections=[]):
        self.name = name
        self.subsections = subsections
    def __str__(self):
        return self.name

class Index():
    def __init__(self, name, sections=[]):
        self.name = name
        self.sections = sections
    def __str__(self):
        return self.name

index = Index("Spark with Scala 2.0")

for d in dirs:

    section_name = " ".join(d.split("-")).title()

    section = Section(section_name, [])

    files = os.listdir(d)

    for f in files:

        first_line = open(d + "/" + f).readline()

        title = first_line.lstrip("#").strip()

        number = f.split(".")[0]

        full_title = number + " - " + title

        subsection = SubSection(full_title, f)

        section.subsections.append(subsection)

    index.sections.append(section)


with open("SUMMARY.md", "w") as f:

    f.write("# Summary\n\n")
    f.write("* [Introduction](README.md)\n")

    for s in index.sections:
        f.write("* " + str(s) + "\n")

        for ss in s.subsections:
            f.write("\t- " + str(ss) + "\n")
