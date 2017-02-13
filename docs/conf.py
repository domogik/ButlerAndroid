
import sys, os

extensions = ['sphinx.ext.autodoc', 'sphinx.ext.todo', 'sphinxcontrib.blockdiag', 'sphinxcontrib.nwdiag', 'sphinxcontrib.seqdiag', 'sphinxcontrib.actdiag']

templates_path = ['_templates']

source_suffix = '.txt'

master_doc = 'contents'

project = u'Butler Android'
copyright = u'2017, Domogik'






version = '0.9'

release = '0.9'

exclude_patterns = ['_build']

pygments_style = 'sphinx'


todo_include_todos=True


html_static_path = []


html_show_sphinx = False


htmlhelp_basename = 'Butler Android'

latex_elements = {

}



latex_documents = [
  ('index', 'ButlerAndroid.tex', u'Butler Android',
   u'Domogik team', 'manual'),
]


























man_pages = [
    ('index', 'domogik', u'Domogik Documentation',
     [u'Domogik team'], 1)
]










texinfo_documents = [
  ('index', 'Domogik', u'Domogik Documentation',
   u'Domogik team', 'Domogik', 'One line description of project.',
   'Miscellaneous'),
]









