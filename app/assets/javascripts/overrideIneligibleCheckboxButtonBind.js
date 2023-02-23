/*
    For the ineligible page, the call handler can override the ineligibility response, but to
    do so, they must check a box to confirm there is evidence to support their eligibility

    This binds the enabled/disabled status of the confirmation button with the confirmation
    checkbox
*/

;(function bindIneligibleContinueButtonWithCheckbox() {
  //var checkbox = $('.button-disable-binding');
  const checkbox = document.querySelector('[name="confirm_eligible"]')
  //var button = $('#continue');
  const button = document.querySelector('#continue')

  if (checkbox && button) {
    checkbox.addEventListener('change', function(e) {
      const isChecked = e.currentTarget.checked;
      if (isChecked) {
        button.removeAttribute('aria-disabled')
        button.removeAttribute('disabled')
      } else {
        button.setAttribute('aria-disabled', 'true')
        button.setAttribute('disabled', 'disabled')
      }
    })

    /*
      The checked attribute must be set on the input when the page loads if:
      - The page has errors
      - The form has data
    */
    if (checkbox.checked) {
      button.removeAttribute('aria-disabled')
      button.removeAttribute('disabled')
    }
  }


})();
